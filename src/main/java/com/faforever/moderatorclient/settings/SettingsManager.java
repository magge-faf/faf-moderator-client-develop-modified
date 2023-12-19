package com.faforever.moderatorclient.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SettingsManager {
    private static final String SETTINGS_FILE_PATH = "src/main/resources/UserSettings.json";
    private static final String DEFAULT_THEME = "/style/main.css";
    private static final String DARK_THEME = "/style/dark-theme.css";

    private final Map<String, String> settings;

    public SettingsManager() {
        this.settings = loadSettings();
    }

    public String getTheme() {
        return settings.getOrDefault("theme", DEFAULT_THEME);
    }

    public void setTheme(String theme) {
        settings.put("theme", theme);
        saveSettings();
    }

    private Map<String, String> loadSettings() {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            byte[] jsonData = Files.readAllBytes(Path.of(SETTINGS_FILE_PATH));
            JsonNode jsonNode = objectMapper.readTree(jsonData);

            Map<String, String> loadedSettings = new HashMap<>();
            JsonNode themeNode = jsonNode.get("theme");
            String theme = themeNode != null ? themeNode.asText() : DEFAULT_THEME;
            loadedSettings.put("theme", theme);

            return loadedSettings;
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private void saveSettings() {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            String jsonString = objectMapper.writeValueAsString(settings);
            Files.write(Path.of(SETTINGS_FILE_PATH), jsonString.getBytes());
        } catch (IOException e) {
            // Handle the exception
        }
    }
}
