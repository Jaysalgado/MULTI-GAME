package com.networks.p2;



public class Main {
    public static void main(String[] args) {
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
                ClientWindow.start();
                break;

            default:
                System.out.println("Invalid argument. Use 'server' or 'client'");
        }
    }
}

