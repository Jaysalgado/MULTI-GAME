package com.networks.p2;

import java.io.*;
import java.net.Socket;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;
import java.security.SecureRandom;


public class ClientThread implements Runnable {
    private final Socket clientSocket;
    private final BlockingQueue<GPacket> buzzQueue;
    private final Server server;

    private DataInputStream input;
    private DataOutputStream output;

    private short clientID;
    private boolean running = true;

    private boolean allowedToAnswer = false;
    private Timer answerTimer;

    public ClientThread(Socket socket, BlockingQueue<GPacket> buzzQueue, Server server) {
        this.clientSocket = socket;
        this.buzzQueue = buzzQueue;
        this.server = server;

        try {
            input = new DataInputStream(clientSocket.getInputStream());
            output = new DataOutputStream(clientSocket.getOutputStream());
            generateClientID();
        } catch (IOException e) {
            System.err.println("[ClientThread] Error setting up streams: " + e.getMessage());
            running = false;
        }
    }

    private void generateClientID() {
        SecureRandom random = new SecureRandom();
        clientID = (short) random.nextInt(Short.MAX_VALUE + 1);
    }

    public short getClientID() {
        return clientID;
    }

    @Override
    public void run() {
        try {
            while (running) {
                byte[] headerBuffer = new byte[1024];
                int read = input.read(headerBuffer);
                if (read == -1) {
                    System.out.println("[ClientThread " + clientID + "] Client disconnected.");
                    break;
                }

                GPacket packet = GPacket.convertFromBytes(headerBuffer);
                if (packet == null) {
                    continue;
                }

                handlePacket(packet);
            }
        } catch (IOException e) {
            System.err.println("[ClientThread " + clientID + "] Connection lost: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void handlePacket(GPacket packet) {
        switch (packet.getType()) {
            case GPacket.TYPE_ANSWER:
                handleAnswer(packet);
                break;
            case GPacket.TYPE_KILL:
                System.out.println("Terminating [ClientThread " + clientID + "]");
                running = false;
                break;
            default:
                System.out.println("[ClientThread " + clientID + "] Unhandled packet type: " + packet.getType());
        }
    }

    public void allowAnswer(long questionTimestamp) {
        allowedToAnswer = true;
        System.out.println("[ClientThread " + clientID + "] Answering...");

        answerTimer = new Timer();
        answerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (allowedToAnswer) {
                    allowedToAnswer = false;
                    server.getClientScores().merge(clientID, -20, Integer::sum);
                    System.out.println("[ClientThread " + clientID + "] Too long! -20 points.");
                    sendPacket(new GPacket(GPacket.TYPE_ANSWER_RES, clientID, questionTimestamp, "timeout".getBytes()));
                }
            }
        }, 10_000);
    }

    private void handleAnswer(GPacket packet) {
        if (!allowedToAnswer) {
            System.out.println("[ClientThread " + clientID + "] unable to answer.");
            return;
        }

        allowedToAnswer = false;
        if (answerTimer != null) answerTimer.cancel();

        String answer = new String(packet.getData()).trim();
        int qIndex = server.getCurrentQuestionIndex();
        Question current = server.getQuestionBank().get(qIndex);
        boolean correct = current.getCorrectAnswerText().equalsIgnoreCase(answer);

        String response = correct ? "correct" : "wrong";
        int scoreDelta = correct ? 10 : - 10;

        server.getClientScores().merge(clientID, scoreDelta, Integer::sum);

        sendPacket(new GPacket(GPacket.TYPE_ANSWER_RES, clientID, System.currentTimeMillis(), response.getBytes()));
        System.out.println("[ClientThread " + clientID + "] Answered " + answer + " => " + result);
    }

    public void sendPacket(GPacket packet) {
        try {
            output.write(packet.convertToBytes());
            output.flush();
        } catch (IOException e) {
            System.err.println("[ClientThread " + clientID + "] Failed to send packet: " + e.getMessage());
            running = false;
        }
    }

    public void sendACK(boolean isWinner, long questionTimestamp) {
        byte type = isWinner ? GPacket.TYPE_ACK : GPacket.TYPE_NEG_ACK;
        GPacket packet = new GPacket(type, clientID, questionTimestamp, null);
        sendPacket(packet);
    }

    public void processBuzzes(long currentQuestionTimestamp) {
        Set<Short> alreadyNotified = new HashSet<>();

        while (!buzzQueue.isEmpty()) {
            GPacket buzz = buzzQueue.poll();
            if (buzz == null) break;

            if (buzz.getTimestamp() != currentQuestionTimestamp) continue;

            short buzzerID = buzz.getNodeID();
            if (alreadyNotified.contains(buzzerID)) continue;

            ClientThread buzzer = activeClients.get(buzzerID);
            if (buzzer != null) {
                if (alreadyNotified.isEmpty()) {
                    // First buzz
                    buzzer.sendACK(true, currentQuestionTimestamp);
                    buzzer.allowAnswer(currentQuestionTimestamp);
                } else {
                    buzzer.sendACK(false, currentQuestionTimestamp);
                }
                alreadyNotified.add(buzzerID);
            }
        }
    }


    private void closeConnection() {
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.err.println("[ClientThread " + clientID + "] Failed to close socket.");
        }
        server.getActiveClients().remove(clientID);
        System.out.println("[ClientThread " + clientID + "] Closed.");
    }
}