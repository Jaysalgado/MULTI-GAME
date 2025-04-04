package com.networks.p2;
import java.io.*;
import java.net.Socket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ClientControl {
    private static Socket tcpSocket;
    private static DatagramSocket sendSocket;
    private static DatagramSocket receiveSocket;
    private static DatagramPacket sendPacket;
    private static DatagramPacket receivePacket;
    private static InetAddress address;
    private static int udpPort = 5005;
    private static DataOutputStream out;
    private static DataInputStream in;
    private static int score;
    private static String[] question;
    private static int next;
    private static boolean status = true;
    private static boolean canBuzz = true;
    private static boolean canAnswer = false;
    private static GameState gameStateListener;
    private static String qNum ="0";
    private static ClientWindow window;

    public ClientControl(ClientWindow w) {
        window = w;

        try {
            tcpSocket = new Socket("localhost", 5555);
            out = new DataOutputStream(tcpSocket.getOutputStream());
            in = new DataInputStream(tcpSocket.getInputStream());
            System.out.println("Client on port 5555");
            receiveSocket = new DatagramSocket(udpPort);
            sendSocket = new DatagramSocket();
            address = InetAddress.getByName("localhost");
            gameStart();
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void setGameStateListener(GameState listener){
        gameStateListener = listener;
    }

    private void gameStart(){
        new Thread(this::tcpListen).start();
        new Thread(this::udpListen).start();
    }

    private void tcpListen(){
        while(true){
            try {

                GPacket packet = GPacket.tcpRead(in);

                switch (packet.getType()) {
                    case GPacket.TYPE_QUESTION:
                        String received = new String(packet.getData());
                        String[] q = received.split("::");
                        setQNum(q[5]);
                        setQuestion(q);
                        setCanBuzz(true);
                        System.out.println("question received " + q[5]);
                        window.startPhaseTimer(15, "Buzz");
                        break;
                    case GPacket.TYPE_NEXT:
                        setCanAnswer(false);
                        System.out.println("Next is now: " + next);
                        break;
                    case GPacket.TYPE_BUZZ_RES:
                        String res = new String(packet.getData(), StandardCharsets.UTF_8);
                        setBuzz(res);
                        System.out.println("Buzz Response: " + res);
                        if (res.equals("ack")) {
                            window.startPhaseTimer(10, "Answer");
                        }
                        break;
                    case GPacket.TYPE_ANSWER_RES:
                        String answerRes = new String(packet.getData(), StandardCharsets.UTF_8);
                        System.out.println("Answer Response: " + answerRes);
                        break;
                    default:
                        System.out.println("Unknown message type: " + packet.getType());
                }

            } catch ( IOException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

    }

    public void sendAnswer(String a){
        try {
            byte[] data = a.getBytes(StandardCharsets.UTF_8);
            GPacket prep = new GPacket(GPacket.TYPE_ANSWER, (short) 0, System.currentTimeMillis(), data);
            byte[] packet = prep.convertToBytes();
            out.write(packet);
            out.flush();
        }  catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void udpListen(){
        while(true){
            try {
                byte[] buffer = new byte[1024];
                receivePacket = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(receivePacket);
                GPacket gPacket = GPacket.convertFromBytes(receivePacket.getData());
                System.out.println("Received UDP packet: " + gPacket);
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    public void buzz() {
        try {
            byte[] data = qNum.getBytes(StandardCharsets.UTF_8);
            GPacket prep = new GPacket(GPacket.TYPE_BUZZ, (short) 0, System.currentTimeMillis(), data);
            byte[] packet = prep.convertToBytes();
            sendPacket = new DatagramPacket(packet, packet.length, address, 6666);
            sendSocket.send(sendPacket);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void setBuzz(String res){
        setCanBuzz(false);
        if (res.equals("ack")) {
            System.out.println("Buzz accepted");
            setCanAnswer(true);
        } else if (res.equals("neg-ack")) {
            System.out.println("Buzz rejected");
            setCanAnswer(false);
        } else {
            System.out.println("Unknown response: " + res);
        }
    }

    private void setQuestion(String[] q){
        question = q;
        if (gameStateListener != null) {
            gameStateListener.onQuestionReceived(q);
        }
    }
    public String[] getQuestion(){

        return question;
    }

    public static boolean isStatus() {
        return status;
    }

    private static void setStatus(boolean status) {
        ClientControl.status = status;
    }

    public static boolean isCanBuzz() {
        return canBuzz;
    }

    private static void setCanBuzz(boolean res) {
        canBuzz = res;
        if (gameStateListener != null) {
            gameStateListener.onCanBuzzChanged(res);
        }
    }

    public static boolean isCanAnswer() {
        return canAnswer;
    }

    private static void setCanAnswer(boolean res) {
        canAnswer = res;
        if (gameStateListener != null) {
            gameStateListener.onCanAnswerChanged(res);
        }
    }

    private static void setQNum(String n){
        qNum = n;
    }

    public static String getQNum() {
        return qNum;
    }


}
