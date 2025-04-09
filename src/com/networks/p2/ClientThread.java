package com.networks.p2;

import java.io.*;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class ClientThread implements Runnable {
    private final Socket clientSocket;
    private final PriorityBlockingQueue<GPacket> buzzQueue;
    private final Server server;
    private int invalidPacketCount = 0;
    private static final int MAX_INVALID_PACKETS = 3;

    private DataInputStream input;
    private DataOutputStream output;

    private short clientID;
    private boolean running = true;

    private boolean allowedToAnswer = false;
    private Timer answerTimer;

    public ClientThread(Socket socket, PriorityBlockingQueue<GPacket> buzzQueue, Server server, short predefinedID) {
        this.clientSocket = socket;
        this.buzzQueue = buzzQueue;
        this.server = server;
        this.clientID = predefinedID;

        try {
            input = new DataInputStream(clientSocket.getInputStream());
            output = new DataOutputStream(clientSocket.getOutputStream());
        } catch (IOException e) {
            System.err.println("[ClientThread] Error setting up streams: " + e.getMessage());
            running = false;
        }
    }

    private void generateClientID() {
        clientID = 0;
//        SecureRandom random = new SecureRandom();
//        clientID = (short) random.nextInt(Short.MAX_VALUE + 1);
//        System.out.println("[ClientThread] Assigned ID: " + clientID);
    }

    public short getClientID() {
        return clientID;
    }

    @Override
    public void run() {
        try {
            while (running) {
                GPacket packet = GPacket.tcpRead(input);
                if (packet != null) {
                    handlePacket(packet);
                }
            }
        } catch (EOFException e) {
            System.out.println("[ClientThread " + clientID + "] Client disconnected.");
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

        answerTimer = new Timer();
        answerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (allowedToAnswer) {
                    allowedToAnswer = false;
                    server.getClientScores().merge(clientID, -20, Integer::sum);
                    sendPacket(new GPacket(GPacket.TYPE_ANSWER_RES, clientID, questionTimestamp, "timeout".getBytes()));
                }
            }
        }, 10_000);
    }

    private void handleAnswer(GPacket packet) {
        if (!allowedToAnswer) {
            System.out.println("[ClientThread " + clientID + "] Answer ignored: not allowed.");
            incrementInvalidAndCheck();
            return;
        }

        allowedToAnswer = false;
        if (answerTimer != null) answerTimer.cancel();

        String answer = new String(packet.getData()).trim();
        int qIndex = server.getCurrentQuestionIndex();
        Question current = server.getQuestionBank().get(qIndex);
        int correctIndex = server.getCorrectAnswers().getOrDefault(qIndex, -1);

        int selectedIndex;
        try {
            selectedIndex = Integer.parseInt(answer);
        } catch (NumberFormatException e) {
            selectedIndex = -1;
        }

        boolean correct = (correctIndex >= 0 && selectedIndex == correctIndex);
        String result = correct ? "correct" : "incorrect";
        int scoreDelta = correct ? 10 : -10;

        server.getClientScores().merge(clientID, scoreDelta, Integer::sum);

        sendPacket(new GPacket(GPacket.TYPE_ANSWER_RES, clientID, System.currentTimeMillis(), result.getBytes()));
    }


    public void sendPacket(GPacket packet) {
        try {
            output.write(packet.convertToBytes());
            output.flush();
        } catch (IOException e) {
            running = false;
        }
    }

    public void sendBuzzResult(boolean isWinner, long questionTimestamp) {
        String result = isWinner ? "ack" : "neg-ack";
        GPacket packet = new GPacket(GPacket.TYPE_BUZZ_RES, clientID, questionTimestamp, result.getBytes());
        sendPacket(packet);
    }

    private void closeConnection() {
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.err.println("[ClientThread " + clientID + "] Error closing socket.");
        }

        server.getPreviousClientScores().put(clientID, server.getClientScores().getOrDefault(clientID, 0));

        if (allowedToAnswer) {
            allowedToAnswer = false;
            server.reprocessBuzzQueue();
        }
        System.out.println("[ClientThread " + clientID + "] Disconnected.");
    }

    private void incrementInvalidAndCheck() {
        invalidPacketCount++;
        if (invalidPacketCount >= MAX_INVALID_PACKETS) {
            System.out.println("[ClientThread " + clientID + "] Sending too many invalid packets. Terminating.");
            sendPacket(new GPacket(GPacket.TYPE_KILL, clientID, System.currentTimeMillis(), "Too many invalid packets".getBytes()));
            closeConnection();
        }
    }

    public boolean isRunning() { return running; }
}
