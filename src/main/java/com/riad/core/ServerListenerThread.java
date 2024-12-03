package com.riad.core;

import com.riad.core.io.WebRootHandler;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;

public class ServerListenerThread extends Thread {

    private int port;
    private String webroot;
    private ServerSocket serverSocket;
    private WebRootHandler webRootHandler;

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerListenerThread.class);

    public ServerListenerThread(int port, String webroot) throws IOException {
        this.port = port;
        this.webroot = webroot;
        this.serverSocket = new ServerSocket(this.port);
        this.webRootHandler = new WebRootHandler(this.webroot);
    }

    @Override
    public void run() {

        try {
            while (serverSocket.isBound() && !serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();

                LOGGER.info("Connection Accepted " + socket.getInetAddress());

                HttpConnectionWorkerThread thread = new HttpConnectionWorkerThread(socket , webRootHandler);
                thread.start();
            }

        } catch (IOException e) {
            LOGGER.error("Problem with setting the socket", e);
        }finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {}
            }
        }
    }
}
