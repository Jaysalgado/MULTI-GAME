package com.networks.p2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.PriorityBlockingQueue;

public class Server {
    private UDPThread udpThread;
    private int TCP_PORT;
    private int UDP_PORT;
    private boolean gameStarted = false;

    private final Map<Short, ClientThread> activeClients = new ConcurrentHashMap<>();
    private final Map<Short, Integer> clientScores = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<GPacket> buzzQueue =
            new PriorityBlockingQueue<>(100, Comparator.comparingLong(GPacket::getTimestamp));
    private final Map<Integer, Integer> correctAnswers = new HashMap<>();
    private final List<Question> questionBank = new ArrayList<>();
    private final Map<Short, Integer> previousClientScores = new ConcurrentHashMap<>();
    private final Map<String, Short> ipToClientID = new HashMap<>();
    private final Set<Short> buzzedClients = ConcurrentHashMap.newKeySet();
    private final Map<Short, Integer> reconnectCounts = new ConcurrentHashMap<>();
    private final Set<Short> bannedClients = ConcurrentHashMap.newKeySet();
    private volatile Short activeBuzzer = null;
    private final Set<Short> processedBuzzers = ConcurrentHashMap.newKeySet();


    private int currentQuestionIndex = 0;
    private GPacket currentQuestionPacket = null;
    private long currentQuestionTimestamp = 0;

    public static void main(String[] args) {
        new Server().startServer();
    }

    public void startServer() {
        ConfigLoader config = new ConfigLoader("src/com/networks/p2/config.txt");

        TCP_PORT = config.getInt("TCP_PORT", 5555);
        UDP_PORT = config.getInt("UDP_PORT", 6666);

        loadQuestions();
        loadAnswers();
        loadStaticClientList();

        udpThread = new UDPThread(UDP_PORT, buzzQueue);
        new Thread(udpThread).start();
        new Thread(this::startAdminConsole).start();

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("[SERVER] Listening on TCP port " + TCP_PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientIP = clientSocket.getInetAddress().getHostAddress();

                Short predefinedID = ipToClientID.get(clientIP);
                if (predefinedID == null) {
                    System.out.println("[SERVER] Connection from unknown IP: " + clientIP + ". Closing.");
                    clientSocket.close();
                    continue;
                }
//                Short predefinedID = ipToClientID.get(clientIP);
//                if (predefinedID == null) {
//                    System.out.println("[SERVER] Connection from unknown IP: " + clientIP + ". Closing.");
//                    clientSocket.close();
//                    continue;
//                }

                if (bannedClients.contains(predefinedID)) {
                    System.out.println("[SERVER] Banned client attempted reconnection - ID: " + predefinedID);
                    clientSocket.close();
                    continue;
                }

                int reconnectCount = reconnectCounts.getOrDefault(predefinedID, 0) + 1;
                reconnectCounts.put(predefinedID, reconnectCount);

                if (reconnectCount > 3) {
                    System.out.println("[SERVER] Client ID " + predefinedID + " exceeded reconnect limit. Banned.");
                    bannedClients.add(predefinedID);
                    clientSocket.close();
                    continue;
                }

                ClientThread clientThread = new ClientThread(clientSocket, buzzQueue, this, predefinedID);
                short clientID = clientThread.getClientID();

                activeClients.put(clientID, clientThread);

                int restoredScore = previousClientScores.getOrDefault(clientID, 0);
                clientScores.put(clientID, restoredScore);

                ByteBuffer rejoinUP = ByteBuffer.allocate(4);
                rejoinUP.putInt(restoredScore);
                GPacket rejoinPacket = new GPacket(GPacket.TYPE_REJOIN, clientID, System.currentTimeMillis(), rejoinUP.array());
                clientThread.sendPacket(rejoinPacket);

                System.out.println("[SERVER] Client connected → ID: " + clientID +
                        " | IP: " + clientSocket.getInetAddress().getHostAddress() +
                        " | Total clients: " + activeClients.size());

                new Thread(clientThread).start();

                if (currentQuestionPacket != null) {
                    clientThread.sendPacket(currentQuestionPacket);
                }

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
            processedBuzzers.clear();
            buzzedClients.clear();

            currentQuestionIndex = qIndex;
            Question q = questionBank.get(qIndex);
            long questionTimestamp = System.currentTimeMillis();

            udpThread.setCurrentQuestionIndex(currentQuestionIndex);

            System.out.println("\n[GAME] Sending Q" + (qIndex + 1) + ": " + q.getText());

            String[] qArray = q.getQuestionArray();
            String joined = String.join("::", qArray);
            GPacket questionPacket = new GPacket(GPacket.TYPE_QUESTION, (short) 0, questionTimestamp, joined.getBytes());

            currentQuestionPacket = questionPacket;
            currentQuestionTimestamp = questionTimestamp;

            for (ClientThread client : activeClients.values()) {
                client.sendPacket(questionPacket);
            }

            System.out.println("[GAME] Buzz phase started.");

            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            boolean someoneBuzzed = processBuzzes(currentQuestionIndex);

            if (someoneBuzzed) {
                System.out.println("[GAME] Waiting for all buzzers to finish answering...");
                while (getActiveBuzzer() != null || hasRemainingBuzzers()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
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
        while (!buzzQueue.isEmpty()) {
            GPacket buzz = buzzQueue.peek();
            if (buzz == null) continue;

            short buzzerID = buzz.getNodeID();
            if (processedBuzzers.contains(buzzerID)) {
                buzzQueue.poll();
                continue;
            }

            String dataStr = new String(buzz.getData()).trim();
            int questionIndex;
            try {
                questionIndex = Integer.parseInt(dataStr);
            } catch (NumberFormatException e) {
                System.out.println("[SERVER] Invalid buzz from client " + buzzerID + ": " + dataStr);
                buzzQueue.poll();
                continue;
            }

            if (questionIndex != currentQuestionIndex) {
                buzzQueue.poll();
                continue;
            }

            ClientThread buzzer = activeClients.get(buzzerID);

            if (buzzer == null || !buzzer.isRunning()) {
                processedBuzzers.add(buzzerID);
                buzzQueue.poll();
                continue;
            }

            buzzer.sendBuzzResult(true, questionTimestamp);
            buzzer.allowAnswer(questionTimestamp);
            buzzedClients.add(buzzerID);
            processedBuzzers.add(buzzerID);
            setActiveBuzzer(buzzerID);

            buzzQueue.poll();
            return true;
        }

        System.out.println("[GAME] No one buzzed in.");
        return false;
    }


    private void endGame() {
        System.out.println("\n[GAME OVER] Final scores:");
        List<Map.Entry<Short, Integer>> sortedScores = new ArrayList<>(clientScores.entrySet());
        sortedScores.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        short winnerID = -1;
        int maxScore = Integer.MIN_VALUE;

        for (Map.Entry<Short, Integer> entry : sortedScores) {
            System.out.println("Client " + entry.getKey() + ": " + entry.getValue());
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                winnerID = entry.getKey();
            }
        }

        System.out.println("🏆 Winner: Client " + winnerID + " with " + maxScore + " points!");

        ByteBuffer scoreBuffer = ByteBuffer.allocate(4 * sortedScores.size()); // 2 bytes for clientID + 2 for score
        for (Map.Entry<Short, Integer> entry : sortedScores) {
            scoreBuffer.putShort(entry.getKey());
            scoreBuffer.putShort(entry.getValue().shortValue()); // cast score to short (assumes within range)
        }

        byte[] scoreData = scoreBuffer.array();
        GPacket scorePacket = new GPacket(GPacket.TYPE_SCORE, (short) 0, System.currentTimeMillis(), scoreData);

        for (ClientThread client : activeClients.values()) {
            client.sendPacket(scorePacket);
        }

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

    private void loadStaticClientList() {
        try (BufferedReader reader = new BufferedReader(new FileReader("src/com/networks/p2/clients.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(",");
                if (parts.length == 2) {
                    short clientID = Short.parseShort(parts[0].trim());
                    String ip = parts[1].trim();
                    ipToClientID.put(ip, clientID);
                }
            }
            System.out.println("[SERVER] Loaded static client ID/IP list.");
        } catch (IOException e) {
            System.err.println("[SERVER] Exception error:Failed to load clients.txt.");
        }
    }

    public synchronized void reprocessBuzzQueue() {
        while (!buzzQueue.isEmpty()) {
            GPacket nextBuzz = buzzQueue.poll();
            if (nextBuzz == null) continue;

            short nextBuzzerID = nextBuzz.getNodeID();
            ClientThread nextClient = activeClients.get(nextBuzzerID);

            if (nextClient == null || !nextClient.isRunning()) {
                continue;
            }

            String dataStr = new String(nextBuzz.getData()).trim();
            int questionIndex;
            try {
                questionIndex = Integer.parseInt(dataStr);
            } catch (NumberFormatException e) {
                System.out.println("[SERVER] Invalid buzz data from client " + nextBuzzerID + ": " + dataStr);
                continue;
            }

            if (questionIndex != currentQuestionIndex) {
                continue;
            }

            System.out.println("[SERVER] Reassigning buzzer: Client " + nextBuzzerID);
            setActiveBuzzer(nextBuzzerID);
            buzzedClients.add(nextBuzzerID);
            nextClient.sendBuzzResult(true, currentQuestionTimestamp);
            nextClient.allowAnswer(System.currentTimeMillis());

            return;
        }

        System.out.println("[SERVER] No remaining valid buzzers in queue.");
    }


    private void startAdminConsole() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            if (input.startsWith("kill")) {
                String[] parts = input.split("\\s+");
                if (parts.length == 2) {
                    try {
                        short clientID = Short.parseShort(parts[1]);
                        ClientThread target = activeClients.get(clientID);
                        if (target != null) {
                            System.out.println("[SERVER] Killing client " + clientID);
                            target.sendPacket(new GPacket(GPacket.TYPE_KILL, clientID, System.currentTimeMillis(), "Killed by admin".getBytes()));
                            target.forceDisconnect();
                        } else {
                            System.out.println("[SERVER] No active client with ID: " + clientID);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("[SERVER] Invalid client ID.");
                    }
                } else {
                    System.out.println("[SERVER] Kill [clientID]");
                }
            } else {
                System.out.println("[SERVER] Try: kill [clientID]");
            }
            if (input.equals("clients")) {
                activeClients.keySet().forEach(id -> System.out.println("Current clients: " + id));
            }
        }
    }

    public synchronized boolean hasRemainingBuzzers() {
        for (GPacket buzz : buzzQueue) {
            short id = buzz.getNodeID();
            if (activeClients.containsKey(id) && activeClients.get(id).isRunning()) {
                return true;
            }
        }
        return false;
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

    public Set<Short> getBuzzedClients() { return buzzedClients; }

    public Map<Short, Integer> getPreviousClientScores() { return previousClientScores; }

    public void incrementQuestionIndex() {
        currentQuestionIndex++;
    }

    public synchronized void setActiveBuzzer(Short id) { this.activeBuzzer = id; }

    public synchronized Short getActiveBuzzer() { return this.activeBuzzer; }

    public synchronized long getCurrentQuestionTimestamp() { return this.currentQuestionTimestamp; }
}
