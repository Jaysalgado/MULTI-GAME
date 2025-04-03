package com.networks.p2;
import java.io.*;
import java.net.Socket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ClientControl {
    private static Socket tcpSocket;
    private static DatagramSocket sendSocket;
    private static DatagramSocket receiveSocket;
    private static DatagramPacket datagramPacket;
    private static InetAddress address;
    private static int udpPort;
    private static DataOutputStream out;
    private static DataInputStream in;
    private static String buzzResponse;
    private static int score;
    private static String[] question;
    private static int next;

    public ClientControl() {

        try {
            tcpSocket = new Socket(12345);
            out = new DataOutputStream(tcpSocket.getOutputStream());
            in = new DataInputStream(tcpSocket.getInputStream());
            System.out.println("Server started on port 12345");
            receiveSocket = new DatagramSocket(udpPort);
            sendSocket = new DatagramSocket();
            address = InetAddress.getByName("localhost");
            gameStart();
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }

    }
    private void gameStart(){
        new Thread(this::tcpListen);
        new Thread(this::udpListen);
    }

    private void tcpListen(){
        while(true){
            try {

                byte message = in.readByte();
                switch (message) {
                    case GPacket.TYPE_QUESTION:
                        break;
                    case GPacket.TYPE_NEXT:
                        System.out.println("Next: " + next);
                        break;
                    case GPacket.TYPE_BUZZ_RES:
                        System.out.println("Buzz Response: " + buzzResponse);
                        break;
                    case GPacket.TYPE_SCORE:
                        System.out.println("Score: " + score);
                        break;
                    default:
                        System.out.println("Unknown message type: " + message);
                }

            } catch ( IOException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

    }
    private void tcpSend(){

    }
    private void udpListen(){

    }
    private void buzz() {

    }
    private void setBuzz(String res){
        buzzResponse = res;
    }
}
