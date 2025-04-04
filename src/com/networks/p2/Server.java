package com.networks.p2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private UDPThread udpThread;
    private int TCP_PORT;
    private int UDP_PORT;
    private boolean gameStarted = false;

    private final Map<Short, ClientThread> activeClients = new ConcurrentHashMap<>();
    private final Map<Short, Integer> clientScores = new ConcurrentHashMap<>();
    private final BlockingQueue<GPacket> buzzQueue = new LinkedBlockingQueue<>();
    private final Map<Integer, Integer> correctAnswers = new HashMap<>();

    private final List<Question> questionBank = new ArrayList<>();

    private int currentQuestionIndex = 0;

    public static void main(String[] args) {
        new Server().startServer();
    }

    public void startServer() {
        ConfigLoader config = new ConfigLoader("src/com/networks/p2/config.txt");

        TCP_PORT = config.getInt("TCP_PORT", 5555);
        UDP_PORT = config.getInt("UDP_PORT", 6666);

        loadQuestions();
        loadAnswers();

        udpThread = new UDPThread(UDP_PORT, buzzQueue);
        new Thread(udpThread).start();


        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("[SERVER] Listening on TCP port " + TCP_PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientThread clientThread = new ClientThread(clientSocket, buzzQueue, this);
                short clientID = clientThread.getClientID();

                activeClients.put(clientID, clientThread);
                clientScores.putIfAbsent(clientID, 0);

                System.out.println("[SERVER] Client connected → ID: " + clientID +
                        " | IP: " + clientSocket.getInetAddress().getHostAddress() +
                        " | Total clients: " + activeClients.size());

                new Thread(clientThread).start();

                if (!gameStarted) {
                    gameStarted = true;

                    new Thread(() -> {
                        try {
                            System.out.println("[SERVER] Waiting 5 seconds for more players to join...");
                            Thread.sleep(5_000);
                            startGameLoop();
                        } catch (InterruptedException e) {
                            System.err.println("[SERVER] Lobby wait interrupted.");
                        }
                    }).start();
                }
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Server error: " + e.getMessage());
            e.printStackTrace();
        }

        startGameLoop();
    }

    public void startGameLoop() {
        final int TOTAL_QUESTIONS = 20;

        for (int qIndex = 0; qIndex < TOTAL_QUESTIONS && qIndex < questionBank.size(); qIndex++) {
            currentQuestionIndex = qIndex;
            Question q = questionBank.get(qIndex);
            long questionTimestamp = System.currentTimeMillis();

            udpThread.setCurrentQuestionIndex(currentQuestionIndex);

            System.out.println("\n[GAME] Sending Q" + (qIndex + 1) + ": " + q.getText());

            String[] qArray = q.getQuestionArray();
            String joined = String.join("::", qArray);
            GPacket questionPacket = new GPacket(GPacket.TYPE_QUESTION, (short) 0, questionTimestamp, joined.getBytes());

            for (ClientThread client : activeClients.values()) {
                client.sendPacket(questionPacket);
            }

            System.out.println("[GAME] Buzz phase started...");

            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            boolean someoneBuzzed = processBuzzes(currentQuestionIndex);

            if (someoneBuzzed) {
                System.out.println("[GAME] Waiting 10 seconds for answer...");
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            GPacket nextPacket = new GPacket(GPacket.TYPE_NEXT, (short) 0, questionTimestamp, null);
            for (ClientThread client : activeClients.values()) {
                client.sendPacket(nextPacket);
            }
        }

        endGame();
    }

    private boolean processBuzzes(long questionTimestamp) {
        System.out.println("[SERVER] Processing buzzes... queue size before: " + buzzQueue.size());
        Set<Short> alreadyProcessed = new HashSet<>();
        boolean someoneBuzzed = false;

        while (!buzzQueue.isEmpty()) {
            GPacket buzz = buzzQueue.poll();
            if (buzz == null) break;

            String dataStr = new String(buzz.getData()).trim();
            int questionIndex;
            try {
                questionIndex = Integer.parseInt(dataStr);
            } catch (NumberFormatException e) {
                System.out.println("[SERVER] Invalid buzz data from client " + buzz.getNodeID() + ": " + dataStr);
                continue;
            }

            if (questionIndex != currentQuestionIndex) continue;

            short buzzerID = buzz.getNodeID();
            if (alreadyProcessed.contains(buzzerID)) continue;

            ClientThread buzzer = activeClients.get(buzzerID);
            if (buzzer != null) {
                if (alreadyProcessed.isEmpty()) {
                    buzzer.sendBuzzResult(true, questionTimestamp);
                    buzzer.allowAnswer(questionTimestamp);
                    someoneBuzzed = true;
                } else {
                    buzzer.sendBuzzResult(false, questionTimestamp);
                }
                alreadyProcessed.add(buzzerID);
            }
        }

        if (!someoneBuzzed) {
            System.out.println("[GAME] No one buzzed in.");
        }

        return someoneBuzzed;
    }

    private void endGame() {
        System.out.println("\n[GAME OVER] Final scores:");
        int maxScore = Integer.MIN_VALUE;
        short winnerID = -1;

        for (Map.Entry<Short, Integer> entry : clientScores.entrySet()) {
            System.out.println("Client " + entry.getKey() + ": " + entry.getValue());
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                winnerID = entry.getKey();
            }
        }

        System.out.println("🏆 Winner: Client " + winnerID + " with " + maxScore + " points!");

        GPacket kill = new GPacket(GPacket.TYPE_KILL, (short) 0, System.currentTimeMillis(), null);
        for (ClientThread client : activeClients.values()) {
            client.sendPacket(kill);
        }
    }

    private void loadQuestions() {
        try (BufferedReader reader = new BufferedReader(new FileReader("src/com/networks/p2/questions.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length != 6) {
                    System.out.println("[SERVER] Skipping malformed line: " + line);
                    continue;
                }

                Question q = new Question(parts);
                questionBank.add(q);
            }

            System.out.println("[SERVER] Loaded " + questionBank.size() + " questions.");
        } catch (IOException e) {
            System.err.println("[SERVER] Failed to load questions.txt: " + e.getMessage());
        }
    }

    private void loadAnswers() {
        try (BufferedReader reader = new BufferedReader(new FileReader("src/com/networks/p2/answers.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length != 2) {
                    System.out.println("[SERVER] Skipping malformed answer line: " + line);
                    continue;
                }

                int questionNum = Integer.parseInt(parts[0].trim());
                int correctIndex = Integer.parseInt(parts[1].trim());

                correctAnswers.put(questionNum, correctIndex);
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Failed to load answers.txt: " + e.getMessage());
        } catch (NumberFormatException e) {
        }
    }

    public List<Question> getQuestionBank() {
        return questionBank;
    }

    public Map<Integer, Integer> getCorrectAnswers() { return correctAnswers; }

    public Map<Short, Integer> getClientScores() {
        return clientScores;
    }

    public Map<Short, ClientThread> getActiveClients() {
        return activeClients;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void incrementQuestionIndex() {
        currentQuestionIndex++;
    }
}
