package com.networks.p2;

public class Main {
    public static void main(String[] args) {
        // --- Option A: Run everything manually with command-line args ---
        /*
        if (args.length < 1) {
            System.out.println("Usage: java Main [server|client]");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "server":
                System.out.println("[Main] Starting server.");
                new Server().startServer();
                break;

            case "client":
                System.out.println("[Main] Starting client.");
                ClientWindow window = new ClientWindow();
                break;

            default:
                System.out.println("Invalid argument. Use 'server' or 'client'");
        }
        */

        // --- Option B: Run both server and client together for local testing ---

        // Start the server in its own thread
        new Thread(() -> {
            new Server().startServer(); // Launch the server
        }).start();

        // Wait a bit to ensure the server starts first
        try {
            Thread.sleep(500); // Half a second delay
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Start the client window
        ClientWindow window = new ClientWindow();
    }
}

