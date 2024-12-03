package com.riad;

import com.riad.config.Configuration;
import com.riad.config.ConfigurationManager;
import com.riad.core.ServerListenerThread;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.io.IOException;

public class HttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);

    public static void main(String[] args) {

        LOGGER.info("Server starting...");

        try {
            ConfigurationManager.getInstance().loadConfigurationFile("src/main/resources/http.json");
            Configuration conf = ConfigurationManager.getInstance().getCurrentConfiguration();

            LOGGER.info("Using Port : " + conf.getPort());
            LOGGER.info("Using WebRootHandler : " + conf.getWebRoot());

            ServerListenerThread thread = new ServerListenerThread(conf.getPort() , conf.getWebRoot());
            thread.start();

        } catch (IOException e) {
            // todo : handle later
            e.printStackTrace();
        }

    }
}