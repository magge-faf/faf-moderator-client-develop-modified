package com.faforever.moderatorclient.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class PreferencesConfig implements DisposableBean {

    @Autowired
    private ObjectMapper objectMapper;

    private final Path preferencesFilePath;
    private Map<String, Map<String, Object>> preferences = new HashMap<>();

    public PreferencesConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.preferencesFilePath = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "Mordor", "user.prefs");
        createPreferencesFileIfMissing();
        readPreferences();
    }

    public void readPreferences() {
        try {
            byte[] bytes = Files.readAllBytes(preferencesFilePath);
            if (bytes.length > 0) {
                this.preferences = objectMapper.readValue(bytes, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                log.info("Reading preferences file `{}`", preferencesFilePath.toAbsolutePath());
            } else {
                log.info("Preferences file `{}` is empty", preferencesFilePath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Error reading preferences file `{}`", preferencesFilePath.toAbsolutePath(), e);
            this.preferences = new HashMap<>();
        }
    }

    public void writePreferences() {
        try (Writer writer = Files.newBufferedWriter(preferencesFilePath, StandardCharsets.UTF_8)) {
            log.info("Writing preferences file `{}`", preferencesFilePath.toAbsolutePath());

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, preferences);

        } catch (IOException e) {
            log.error("Preferences file `{}` could not be written", preferencesFilePath.toAbsolutePath(), e);
            throw new RuntimeException(e);
        }
    }

    public void createPreferencesFileIfMissing() {
        Path parent = preferencesFilePath.getParent();
        try {
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            log.warn("Could not create directory `{}`", parent, e);
        }
    }

    public void setPreference(String section, String key, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Preference value cannot be null");
        }

        Map<String, Object> sectionMap = preferences.computeIfAbsent(section, k -> new HashMap<>());

        if (value instanceof double[]) {
            value = Arrays.stream((double[]) value)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining(","));
        }

        sectionMap.put(key, value);
        writePreferences();
    }

    public <T> T getPreference(String section, String key, T defaultValue, Class<T> type) {
        Map<String, Object> sectionMap = preferences.get(section);

        if (sectionMap != null) {
            Object value = sectionMap.get(key);

            if (type.isInstance(value)) {
                return type.cast(value);
            }
        }

        return defaultValue;
    }

    public Boolean getDebugMode() {
        return getPreference("user", "debugMode", false, Boolean.class);
    }

    public String getInitialPageSize() {
        return getPreference("user", "initialPageSize", "100", String.class);
    }

    public String getBrowser() {
        return getPreference("user","browserComboBox", "", String.class);
    }

    public String getStartingTab() {
        return getPreference("user","startingTab", "", String.class);
    }

    public void setPrefColumnWidthPreference(String preferenceKey, double prefWidth) {
        setPreference("prefColumnWidths", preferenceKey, prefWidth);
    }

    public void setColumnOrderPreference(List<String> columnOrder, String tabName) {
        setPreference("columnOrder", tabName, columnOrder);
    }

    public List<String> getColumnOrderPreference(String tabName) {
        return getPreference("columnOrder", tabName, new ArrayList<>(), List.class);
    }

    public double[] getDividerPositions() {
        String savedValue = getPreference("splitPaneDividerPositions", "reportWindow", "", String.class);

        if (savedValue == null || savedValue.isEmpty()) {
            return new double[]{};
        }

        return Arrays.stream(savedValue.split(","))
                .mapToDouble(Double::parseDouble)
                .toArray();
    }

    // Reports Tab Settings

    public boolean getEnforceRatingCheckBox() {
        return getPreference("generalSettings", "enforceRatingCheckBox", false, Boolean.class);
    }

    public boolean getGameResultCheckBox() {
        return getPreference("generalSettings", "gameResultCheckBox", false, Boolean.class);
    }

    public boolean getJsonStatsCheckBox() {
        return getPreference("generalSettings", "jsonStatsCheckBox", false, Boolean.class);
    }

    public boolean getGameEndedCheckBox() {
        return getPreference("generalSettings", "gameEndedCheckBox", false, Boolean.class);
    }

    public boolean getFilterLogCheckBox() {
        return getPreference("generalSettings", "filterLogCheckBox", true, Boolean.class);
    }

    public boolean getAutomaticallyLoadChatLogCheckBox() {
        return getPreference("generalSettings", "automaticallyLoadChatLogCheckBox", true, Boolean.class);
    }

    public boolean getFocusArmyFromFilterCheckBox() {
        return getPreference("filterSettings", "focusArmyFromFilterCheckBox", false, Boolean.class);
    }

    public boolean getPingOfTypeAlertFilterCheckBox() {
        return getPreference("filterSettings", "pingOfTypeAlertFilterCheckBox", false, Boolean.class);
    }

    public boolean getPingOfTypeMoveFilterCheckBox() {
        return getPreference("filterSettings", "pingOfTypeMoveFilterCheckBox", false, Boolean.class);
    }

    public boolean getPingOfTypeAttackFilterCheckBox() {
        return getPreference("filterSettings", "pingOfTypeAttackFilterCheckBox", false, Boolean.class);
    }

    public boolean getSelfDestructionFilterCheckBox() {
        return getPreference("filterSettings", "selfDestructionFilterCheckBox", false, Boolean.class);
    }

    public String getSelfDestructionFilterAmountTextField() {
        return getPreference("filterSettings", "selfDestructionFilterAmountTextField", "0", String.class);
    }

    public boolean getTextMarkerTypeFilterCheckBox() {
        return getPreference("filterSettings", "textMarkerTypeFilterCheckBox", false, Boolean.class);
    }

    // User Management Settings

    public boolean getIncludeUUIDCheckBox() {
        return getPreference("filterSettings", "includeUUIDCheckBox", false, Boolean.class);
    }

    public boolean getIncludeUIDHashCheckBox() {
        return getPreference("filterSettings", "includeUIDHashCheckBox", false, Boolean.class);
    }

    public boolean getIncludeMemorySerialNumberCheckBox() {
        return getPreference("filterSettings", "includeMemorySerialNumberCheckBox", false, Boolean.class);
    }

    public boolean getIncludeVolumeSerialNumberCheckBox() {
        return getPreference("filterSettings", "includeVolumeSerialNumberCheckBox", false, Boolean.class);
    }

    public boolean getIncludeSerialNumberCheckBox() {
        return getPreference("filterSettings", "includeSerialNumberCheckBox", false, Boolean.class);
    }

    public boolean getIncludeProcessorIdCheckBox() {
        return getPreference("filterSettings", "includeProcessorIdCheckBox", false, Boolean.class);
    }

    public boolean getIncludeManufacturerCheckBox() {
        return getPreference("filterSettings", "includeManufacturerCheckBox", false, Boolean.class);
    }

    public boolean getIncludeIPCheckBox() {
        return getPreference("filterSettings", "includeIPCheckBox", false, Boolean.class);
    }

    public boolean getIncludeProcessorNameCheckBox() {
        return getPreference("filterSettings", "includeProcessorNameCheckBox", false, Boolean.class);
    }

    public boolean getCatchFirstLayerSmurfsOnlyCheckBox() {
        return getPreference("filterSettings", "catchFirstLayerSmurfsOnlyCheckBox", false, Boolean.class);
    }

    public Integer getDepthScanningInputTextField() {
        return getPreference("filterSettings", "depthScanningInputTextField", 1000, Integer.class);
    }

    public Integer getMaxUniqueUsersThresholdTextField() {
        return getPreference("filterSettings", "maxUniqueUsersThresholdTextField", 100, Integer.class);
    }

    public Integer getAmountTextFieldRecentAccountsForSmurfsAmount() {
        return getPreference("filterSettings", "amountTextFieldRecentAccountsForSmurfsAmount", 100, Integer.class);
    }

    public String getPagesMaxAmountGamesTextfield() {
        return getPreference("generalSettings", "pagesMaxAmountGamesTextfield", "10", String.class);
    }

    @Override
    public void destroy() {
        writePreferences();
    }
}
