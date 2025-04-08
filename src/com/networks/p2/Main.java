package com.networks.p2;

public class Main {
    public static void main(String[] args) {

                if (args.length == 0) {
                    System.out.println("[Main] No arguments provided. Running both server and client");

                    // Start server in background
                    new Thread(() -> new Server().startServer()).start();

                    // Delay to let server start
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // Start client
                    new ClientWindow();
                    return;
                }

                // Handle argument-based launch
                switch (args[0].toLowerCase()) {
                    case "server":
                        System.out.println("[Main] Starting server only.");
                        new Server().startServer();
                        break;

                    case "client":
                        System.out.println("[Main] Starting client only.");
                        new ClientWindow();
                        break;

                    default:
                        System.out.println("Usage: java Main [server|client]");
                }
            }
        }



