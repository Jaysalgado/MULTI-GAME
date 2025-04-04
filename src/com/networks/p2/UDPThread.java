package com.networks.p2;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class UDPThread implements Runnable {
    private final int udpPort;
    private final BlockingQueue<GPacket> buzzQueue;
    private DatagramSocket socket;
    private boolean running = true;

    private final Map<Short, Long> lastBuzzTimestamps = new ConcurrentHashMap<>();
    private volatile int currentQuestionIndex = -1;

    public UDPThread(int udpPort, BlockingQueue<GPacket> buzzQueue) {
        this.udpPort = udpPort;
        this.buzzQueue = buzzQueue;
    }

    public void setCurrentQuestionIndex(int index) {
        this.currentQuestionIndex = index;
        lastBuzzTimestamps.clear();
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(udpPort);

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (running) {
                socket.receive(packet);
                byte[] data = packet.getData();
                int length = packet.getLength();

                GPacket gPacket = GPacket.convertFromBytes(data);
                if (gPacket == null || gPacket.getType() != GPacket.TYPE_BUZZ) {
                    continue;
                }

                short clientID = gPacket.getNodeID();
                String dataString = new String(gPacket.getData()).trim();
                int questionIndex = -1;

                try {
                    questionIndex = Integer.parseInt(dataString);
                } catch (NumberFormatException e) {
                    System.out.println("[UDPThread] Invalid buzz data from client " + clientID + ": " + dataString);
                    continue;
                }

                if (questionIndex != currentQuestionIndex) {
                    System.out.println("[UDPThread] Buzz for wrong question. Client " + clientID + " sent: " + questionIndex + ", expected: " + currentQuestionIndex);
                    continue;
                }

                if (lastBuzzTimestamps.containsKey(clientID)) {
                    System.out.println("[UDPThread] Duplicate buzz from client " + clientID + " ignored.");
                    continue;
                }

                lastBuzzTimestamps.put(clientID, System.currentTimeMillis());
                buzzQueue.put(gPacket);
                System.out.println("[UDPThread] Buzz accepted from client " + clientID);
            }
        } catch (SocketException e) {
            System.err.println("[UDPThread] Socket closed or unavailable: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[UDPThread] I/O error: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("[UDPThread] Thread interrupted while putting into queue.");
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("[UDPThread] Shutdown complete.");
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
