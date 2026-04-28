package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.ui.Controller;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class ExcludedHardwareItemsController implements Controller<VBox> {
    @FXML
    public CheckBox keepCommentFieldCheckBox;
    @FXML
    public CheckBox keepReasonFieldCheckBox;
    @FXML
    public Button openExcludedItemsFolder;
    @FXML
    public Button addItemButton;
    @FXML
    public Button createJsonBackup;
    @FXML
    public Button openJsonFile;

    @Getter
    @FXML
    private VBox root;

    @FXML
    private Label statsExcludedItemsLabel;
    @FXML
    private TextField ipAddressField, commentField, reasonField,
            uuidField, deviceIdField, manufacturerField,
            cpuNameField, processorIdField, serialNumberField,
            volumeSerialNumberField, hashField, memorySerialNumberField;

    private final File EXCLUDED_ITEMS_FILE = new File("data/excluded_items.json");
    private final Map<String, TextField> fieldsMap = new LinkedHashMap<>();

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "ip", "IP",
            "uniqueIdAssignments.uniqueId.uuid", "UUID",
            "uniqueIdAssignments.uniqueId.deviceId", "Device ID",
            "uniqueIdAssignments.uniqueId.manufacturer", "Manufacturer",
            "uniqueIdAssignments.uniqueId.name", "CPU Name",
            "uniqueIdAssignments.uniqueId.processorId", "Processor ID",
            "uniqueIdAssignments.uniqueId.serialNumber", "Serial Number",
            "uniqueIdAssignments.uniqueId.volumeSerialNumber", "Volume Serial Number",
            "uniqueIdAssignments.uniqueId.hash", "Hash",
            "uniqueIdAssignments.uniqueId.memorySerialNumber", "Memory Serial Number"
    );

    @FXML
    public void initialize() {
        setupFieldsMap();
        setupFieldListeners();
        updateStatsDisplay();
    }

    private void setupFieldsMap() {
        fieldsMap.put("ip", ipAddressField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.uuid", uuidField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.deviceId", deviceIdField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.manufacturer", manufacturerField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.name", cpuNameField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.processorId", processorIdField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.serialNumber", serialNumberField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.volumeSerialNumber", volumeSerialNumberField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.hash", hashField);
        fieldsMap.put("uniqueIdAssignments.uniqueId.memorySerialNumber", memorySerialNumberField);
    }

    private void setupFieldListeners() {
        fieldsMap.forEach((fieldName, field) ->
                field.textProperty().addListener((obs, oldValue, newValue) -> validateField(field, fieldName, newValue))
        );
    }

    private void validateField(TextField field, String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            resetFieldStyle(field);
        } else if (isValueExcluded(fieldName, value)) {
            field.setStyle("-fx-background-color: red; -fx-text-fill: black;");
        } else {
            field.setStyle("-fx-background-color: lightgreen; -fx-text-fill: black;");
        }
    }

    private boolean isValueExcluded(String fieldName, String value) {
        if (!EXCLUDED_ITEMS_FILE.exists()) return false;

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map data = mapper.readValue(EXCLUDED_ITEMS_FILE, Map.class);
            List<Map<String, Object>> excludedItems = (List<Map<String, Object>>) data.getOrDefault("excluded_items", Collections.emptyList());

            return excludedItems.stream().anyMatch(item -> value.equals(item.get(fieldName)));
        } catch (IOException e) {
            log.error("Failed to read excluded items: {}", e.getMessage());
            return false;
        }
    }

    public void handleOpenExcludedItemsFolder() {
        if (!EXCLUDED_ITEMS_FILE.exists()) {
            log.warn("Excluded items file does not exist.");
            return;
        }
        try {
            new ProcessBuilder("explorer.exe", EXCLUDED_ITEMS_FILE.getParentFile().getAbsolutePath()).start();
        } catch (IOException e) {
            log.error("Failed to open folder: {}", e.getMessage());
        }
    }

    public void handleAddItem() {
        // Collect only non-empty field values
        Map<String, Object> newItem = fieldsMap.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().getText()))
                .filter(e -> e.getValue() != null && !e.getValue().toString().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        putIfNotEmpty(newItem, "comment", commentField.getText());
        putIfNotEmpty(newItem, "reason", reasonField.getText());

        if (newItem.isEmpty()) {
            showAlert();
            log.warn("No data entered. Item not added.");
            return;
        }

        newItem.put("AddedOn", LocalDateTime.now().toString());

        saveExcludedItem(newItem);
        clearUserInputFields();
        updateStatsDisplay();
    }

    private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) map.put(key, value);
    }

    public void saveExcludedItem(Map<String, Object> newItem) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            Map<String, List<Map<String, Object>>> rootData;
            if (EXCLUDED_ITEMS_FILE.exists() && EXCLUDED_ITEMS_FILE.length() > 0) {
                TypeReference<Map<String, List<Map<String, Object>>>> typeRef = new TypeReference<>() {
                };
                rootData = mapper.readValue(EXCLUDED_ITEMS_FILE, typeRef);
            } else {
                rootData = new HashMap<>();
            }

            List<Map<String, Object>> itemList = rootData.getOrDefault("excluded_items", new ArrayList<>());

            boolean exists = itemList.stream().anyMatch(item -> {
                for (String key : newItem.keySet()) {
                    if (newItem.get(key).equals(item.get(key))) {
                        return true; // duplicate found
                    }
                }
                return false;
            });

            if (!exists) {
                itemList.add(newItem);
                rootData.put("excluded_items", itemList);
                mapper.writerWithDefaultPrettyPrinter().writeValue(EXCLUDED_ITEMS_FILE, rootData);
                log.debug("Added new excluded item: {}", newItem);
            } else {
                log.warn("Excluded item already exists: {}", newItem);
            }
        } catch (IOException e) {
            log.error("Failed to save excluded item: {}", e.getMessage());
        }
    }

    private void clearUserInputFields() {
        fieldsMap.values().forEach(field -> {
            field.clear();
            resetFieldStyle(field);
        });

        if (!keepCommentFieldCheckBox.isSelected()) commentField.clear();
        if (!keepReasonFieldCheckBox.isSelected()) reasonField.clear();
    }

    private void resetFieldStyle(TextField field) {
        field.setStyle("-fx-background-color: 5b646a;");
    }

    private void showAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Input Required");
        alert.setHeaderText(null);
        alert.setContentText("Please fill in at least one field before adding.");
        alert.showAndWait();
    }

    void updateStatsDisplay() {
        if (!EXCLUDED_ITEMS_FILE.exists() || EXCLUDED_ITEMS_FILE.length() == 0) {
            Platform.runLater(() -> statsExcludedItemsLabel.setText("No excluded items found."));
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> rootData = mapper.readValue(EXCLUDED_ITEMS_FILE, Map.class);
            List<Map<String, Object>> itemList = (List<Map<String, Object>>) rootData.getOrDefault("excluded_items", Collections.emptyList());

            if (itemList.isEmpty()) {
                Platform.runLater(() -> statsExcludedItemsLabel.setText("No excluded items found."));
                return;
            }

            Map<String, Integer> stats = countNonEmptyFields(itemList);

            StringBuilder sb = new StringBuilder();
            sb.append("Excluded items: ").append(itemList.size());

            List<String> nonZeroStats = new ArrayList<>();
            stats.forEach((key, count) -> {
                if (count > 0) {
                    nonZeroStats.add(DISPLAY_NAMES.getOrDefault(key, key) + ": " + count);
                }
            });

            if (!nonZeroStats.isEmpty()) {
                sb.append(" (").append(String.join(", ", nonZeroStats)).append(")");
            }

            String text = sb.toString();
            Platform.runLater(() -> statsExcludedItemsLabel.setText(text));

        } catch (IOException e) {
            log.error("Failed to read excluded items for stats: {}", e.getMessage());
            Platform.runLater(() -> statsExcludedItemsLabel.setText("Error reading stats."));
        }
    }

    private static @NotNull Map<String, Integer> countNonEmptyFields(List<Map<String, Object>> itemList) {
        Map<String, Integer> statsCount = new LinkedHashMap<>();
        String[] keys = {
                "ip",
                "uniqueIdAssignments.uniqueId.uuid",
                "uniqueIdAssignments.uniqueId.deviceId",
                "uniqueIdAssignments.uniqueId.manufacturer",
                "uniqueIdAssignments.uniqueId.name", // CPU Names
                "uniqueIdAssignments.uniqueId.processorId",
                "uniqueIdAssignments.uniqueId.serialNumber",
                "uniqueIdAssignments.uniqueId.volumeSerialNumber",
                "uniqueIdAssignments.uniqueId.hash",
                "uniqueIdAssignments.uniqueId.memorySerialNumber"
        };
        Arrays.stream(keys).forEach(k -> statsCount.put(k, 0));

        for (Map<String, Object> item : itemList) {
            for (String key : keys) {
                Object value = item.get(key);
                if (value != null && !value.toString().trim().isEmpty()) {
                    statsCount.put(key, statsCount.get(key) + 1);
                }
            }
        }

        return statsCount;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    public void handleOpenJsonFile() {
        if (!EXCLUDED_ITEMS_FILE.exists()) {
            log.warn("Excluded items file not found: {}", EXCLUDED_ITEMS_FILE.getAbsolutePath());
            showAlert(Alert.AlertType.WARNING, "File Not Found", "The excluded_items.json file does not exist.");
            return;
        }

        try {
            new ProcessBuilder("notepad.exe", EXCLUDED_ITEMS_FILE.getAbsolutePath()).start();
            log.info("Opened excluded_items.json in Notepad.");
        } catch (IOException e) {
            log.error("Failed to open excluded_items.json file: {}", e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Open File Failed", "Could not open JSON file:\n" + e.getMessage());
        }
    }

    @FXML
    public void handleOnCreateJsonBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupPath = Paths.get("data", "excluded_items_backup_" + timestamp + ".json");

        try {
            if (!EXCLUDED_ITEMS_FILE.exists()) {
                log.warn("Excluded items file not found for backup: {}", EXCLUDED_ITEMS_FILE.getAbsolutePath());
                showAlert(Alert.AlertType.WARNING, "Backup Failed", "The excluded_items.json file does not exist.");
                return;
            }

            Files.createDirectories(backupPath.getParent());
            Files.copy(EXCLUDED_ITEMS_FILE.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Backup successfully created at: {}", backupPath.toAbsolutePath());
            showAlert(Alert.AlertType.INFORMATION, "Backup Created", "Backup successfully created at:\n" + backupPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to create backup file at {}: {}", backupPath, e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Backup Failed", "An error occurred while creating the backup:\n" + e.getMessage());
        }
    }
}

