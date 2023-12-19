package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.ui.Controller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class SettingsController implements Controller<Region> {

    public ChoiceBox<String> themeChoiceBox;
    @FXML
    private VBox root;

    private static final String DEFAULT_THEME = "/style/main.css";
    private static final String DARK_THEME = "/style/dark-theme.css";
    private static final String SETTINGS_FILE_PATH = "src/main/resources/UserSettings.json";
    private Map settings;

    @Override
    public VBox getRoot() {
        return root;
    }

    @FXML
    private void initialize() {
        loadSettings();
        String selectedTheme = getSelectedTheme();
        themeChoiceBox.setValue(selectedTheme);
    }

    private void loadSettings() {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            byte[] jsonData = Files.readAllBytes(Path.of(SETTINGS_FILE_PATH));
            JsonNode jsonNode = objectMapper.readTree(jsonData);
            settings = objectMapper.convertValue(jsonNode, Map.class);
        } catch (IOException e) {
            log.error("Error reading settings file", e);
            settings = new HashMap<>(); // Use default settings if file reading fails
        }
    }

    public String getSelectedTheme() {
        log.debug("Current selected theme:" + settings.getOrDefault("theme", DEFAULT_THEME));
        return (String) settings.getOrDefault("theme", DEFAULT_THEME);
    }

    private String getCssPathForTheme(String selectedTheme) {
        return switch (selectedTheme) {
            case "Default Theme" -> DEFAULT_THEME;
            case "Dark Theme" -> DARK_THEME;
            default -> DEFAULT_THEME;
        };
    }

    @FXML
    private void onThemeChoiceChanged() {
        String selectedTheme = themeChoiceBox.getValue();
        settings.put("theme", getCssPathForTheme(selectedTheme));
        //saveSettings(); // Test this later
    }

}