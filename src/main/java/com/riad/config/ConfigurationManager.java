package com.riad.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.riad.util.Json;


import java.io.FileReader;
import java.io.IOException;

public class ConfigurationManager {

    private static ConfigurationManager configurationManager;
    private static Configuration currConfiguration;

    private ConfigurationManager() {}

    public static ConfigurationManager getInstance() {
        if(configurationManager == null) {
            configurationManager = new ConfigurationManager();
        }
        return configurationManager;
    }

    public void loadConfigurationFile(String filePath) throws IOException {
        FileReader fileReader = new FileReader(filePath);

        StringBuffer sb = new StringBuffer();
        int i ;
        while ((i = fileReader.read()) != -1){
            sb.append((char) i);
        }

        JsonNode conf = Json.parse(sb.toString());
        System.out.println(conf);
        currConfiguration = Json.fromJson(conf, Configuration.class);
    }

    public Configuration getCurrentConfiguration(){
        if(currConfiguration == null) {
            throw new HttpConfigurationException("No configuration found");
        }
        return currConfiguration;
    }
}
