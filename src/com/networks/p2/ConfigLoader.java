package com.networks.p2;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {
    private final Map<String, String> configMap = new HashMap<>();

    public ConfigLoader(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    configMap.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading config file: " + e.getMessage());
        }
    }

    public String get(String key) {
        return configMap.get(key);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(configMap.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
