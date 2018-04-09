package me.lightless.server;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) throws Exception {
        // Receive unserialize data from socket, in real world this may be a JBOSS, Web, or others...
        ServerSocket serverSocket = new ServerSocket(9999);
        System.out.println("Server listen on: " + serverSocket.getLocalPort());
        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("Connection from " + socket.getInetAddress());
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            try {
                Object object = objectInputStream.readObject();
                System.out.println("Read object done. Object: " + object);
            } catch (Exception e) {
                System.out.println("Error when read object!");
                e.printStackTrace();
            }
        }
    }
}
