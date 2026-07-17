package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.config.local.LocalPreferencesReaderWriter;
import com.faforever.moderatorclient.replay.ReplayStorageService;
import com.faforever.moderatorclient.update.ApplicationUpdateService;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.MainController;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

@Component
@Slf4j
@RequiredArgsConstructor
public class SettingsController implements Controller<Pane> {
    private final LocalPreferences localPreferences;
    private final MainController mainController;
    private final ApplicationUpdateService applicationUpdateService;
    private final ReplayStorageService replayStorageService;

    public VBox root;
    @FXML
    public CheckBox rememberLoginCheckBox;
    @FXML
    public CheckBox darkModeCheckBox;
    @FXML
    public ComboBox<Tab> defaultActiveTabComboBox;
    @FXML
    public Button openAiPromptButton;

    @FXML
    public ComboBox<String> browserComboBox;
    @FXML
    public CheckBox fetchBansOnStartupCheckBox;
    @FXML
    public CheckBox ircDebugTrafficCheckBox;
    @FXML
    public CheckBox automaticConfigurationBackupsOnExitCheckBox;
    @FXML
    public CheckBox autoPurgeTempReplaysOlderThanOneDayCheckBox;
    @FXML
    public CheckBox enableManualReplayLookupCheckBox;
    @FXML
    public CheckBox showReportPlayerRoleLabelsCheckBox;
    @FXML
    public Label replayFolderInfoLabel;
    @FXML
    public Label replayFolderStatusLabel;
    @FXML
    public TextField updateBackupFolderTextField;
    @FXML
    public Label updateBackupFolderInfoLabel;
    @FXML
    public Label updateBackupFolderStatusLabel;
    @FXML
    public Label updateBackupFolderDefaultLabel;

    private String defaultUpdateBackupFolder;

    @Override
    public VBox getRoot() {return root;}

    @FXML
    public void initialize() throws IOException {
        defaultActiveTabComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Tab tab) {
                if (tab == null) return "";
                else return tab.getText();
            }

            @Override
            public Tab fromString(String s) {
                return null;
            }
        });
        log.debug(defaultActiveTabComboBox.getSelectionModel().toString());

        rememberLoginCheckBox.setSelected(localPreferences.getAutoLogin().isEnabled());
        darkModeCheckBox.setSelected(localPreferences.getTabSettings().isDarkModeCheckBox());

        mainController.getRoot().getTabs().forEach(tab -> {
            defaultActiveTabComboBox.getItems().add(tab);
            if (Objects.equals(tab.getId(), localPreferences.getUi().getStartUpTab())) {
                defaultActiveTabComboBox.getSelectionModel().select(tab);
                log.debug(tab.getText());
            }
        });

        fetchBansOnStartupCheckBox.setSelected(localPreferences.getTabSettings().isFetchBansOnStartupCheckBox());
        ircDebugTrafficCheckBox.setSelected(localPreferences.getTabIrcChat().isDebugTraffic());
        ircDebugTrafficCheckBox.selectedProperty().addListener((obs, oldVal, newVal) ->
                localPreferences.getTabIrcChat().setDebugTraffic(newVal));
        autoPurgeTempReplaysOlderThanOneDayCheckBox.setSelected(
                localPreferences.getTabSettings().isAutoPurgeTempReplaysOlderThanOneDayCheckBox()
        );
        automaticConfigurationBackupsOnExitCheckBox.setSelected(
                localPreferences.getTabSettings().isAutomaticConfigurationBackupsOnExitCheckBox()
        );
        enableManualReplayLookupCheckBox.setSelected(
                localPreferences.getTabReports().isEnableManualReplayLookupCheckBox()
        );
        enableManualReplayLookupCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            localPreferences.getTabReports().setEnableManualReplayLookupCheckBox(newVal);
            mainController.refreshManualReplayLookupVisibility();
        });
        showReportPlayerRoleLabelsCheckBox.setSelected(
                localPreferences.getTabReports().isShowReportPlayerRoleLabelsCheckBox()
        );
        showReportPlayerRoleLabelsCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            localPreferences.getTabReports().setShowReportPlayerRoleLabelsCheckBox(newVal);
            mainController.refreshReportPlayerRoleLabelsVisibility();
        });

        defaultUpdateBackupFolder = applicationUpdateService.resolveDefaultBackupDirectory().toString();
        String configuredBackupFolder = localPreferences.getTabSettings().getUpdateBackupFolder();
        updateBackupFolderTextField.setText(
                configuredBackupFolder == null || configuredBackupFolder.isBlank()
                        ? defaultUpdateBackupFolder
                        : configuredBackupFolder
        );
        updateBackupFolderTextField.setPromptText(defaultUpdateBackupFolder);
        updateBackupFolderTextField.textProperty().addListener((obs, oldVal, newVal) -> refreshUpdateBackupFolderInfo());
        replayFolderInfoLabel.setText(String.format(
                Locale.ROOT,
                "Replay folder: %s. All replay downloads and temp replay files are stored here.",
                replayStorageService.resolveReplayDirectory()
        ));
        updateBackupFolderDefaultLabel.setText(String.format(
                Locale.ROOT,
                "Default folder: %s. Leave the field empty or set it to this path to use the default.",
                defaultUpdateBackupFolder
        ));
        replayFolderStatusLabel.setText("");
        refreshUpdateBackupFolderInfo();
        refreshReplayFolderInfo();

        if (browserComboBox.getValue() == null) {
            browserComboBox.setValue(localPreferences.getUi().getBrowserComboBox());
            log.debug("BrowserComboBox value: {}", browserComboBox.getValue());
        }

        browserComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                localPreferences.getUi().setBrowserComboBox(newVal);
            }
        });

        // Initialize templates and bind UI fields
        initTemplatesAndReasons();
        initTemplatesFinishReports();
        createTemplateGamingModeratorTask();
        bindUIElementsToPreferences();
    }
    private void bindUIElementsToPreferences() {
        LocalPreferences.TabReports tabReports = localPreferences.getTabReports();

        for (Field fxField : this.getClass().getDeclaredFields()) {
            fxField.setAccessible(true);
            String fieldName = fxField.getName();

            try {
                // Try to find a matching field in TabReports
                Field prefField = LocalPreferences.TabReports.class.getDeclaredField(fieldName);
                prefField.setAccessible(true);

                Object node = fxField.get(this);
                Object value = prefField.get(tabReports);

                if (node instanceof TextField textField && value instanceof String) {
                    textField.setText((String) value);
                    textField.textProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            prefField.set(tabReports, newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                } else if (node instanceof TextArea textArea && value instanceof String) {
                    textArea.setText((String) value);
                    textArea.textProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            prefField.set(tabReports, newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                } else if (node instanceof CheckBox checkBox && value instanceof Boolean) {
                    checkBox.setSelected((Boolean) value);
                    checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            prefField.set(tabReports, newVal);
                            refreshManualReplayLookupVisibility(fieldName);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                } else if (node instanceof TitledPane titledPane && value instanceof Boolean) {
                    titledPane.setExpanded((Boolean) value);
                    titledPane.expandedProperty().addListener((obs, oldVal, newVal) -> {
                        try {
                            prefField.set(tabReports, newVal);
                        } catch (IllegalAccessException ignored) {
                        }
                    });
                }

            } catch (NoSuchFieldException e) {
                // Controller field has no matching preference field, skip it
            } catch (IllegalAccessException e) {
                log.warn("Cannot access field {}", fieldName, e);
            }
        }
    }

    private void refreshManualReplayLookupVisibility(String fieldName) {
        if ("enableManualReplayLookupCheckBox".equals(fieldName)) {
            mainController.refreshManualReplayLookupVisibility();
        }
    }


    private static final String jsonFileTemplatesAndReasons = CONFIGURATION_FOLDER + File.separator + "templatesAndReasons.json";
    private static final String JSON_CONTENT_templatesAndReasons = """
            {
              "templates": [
                {
                  "name": "Standard Ban",
                  "format": "{reportIds}\\n\\nDAY_NUMBER Day Ban - ReplayID {gameIds} - {reason}"
                },
                {
                  "name": "Another Ban Template",
                  "format": "{reportIds}\\n\\nDAY_NUMBER Day Ban - ReplayID {gameIds} - {reason}"
                }
              ],
              "reasons": [
                "Toxicity",
                "Homophobic Behavior",
                "Reclaiming Friendly Units",
                "Attacking Friendly Units",
                "CTRL+K All Units in Fullshare/Union Game Mode",
                "CTRL+K ACU in Union Game Mode",
                "Harassment via Private Chat",
                "Leaving on Own Terms/Game Ruining",
                "Abuse of Exploits",
                "Bad/Illegal Username",
                "Leaving Before 5-Minute Rule",
                "Offensive Game Titles",
                "Offensive Kick Messages",
                "Racism"
              ]
            }""";

    private void initTemplatesAndReasons(){
        File file = new File(jsonFileTemplatesAndReasons);
        if (!file.exists()) {
            try {
                Files.createDirectories(Paths.get(CONFIGURATION_FOLDER));
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(JSON_CONTENT_templatesAndReasons);
                    log.info("Created {}", jsonFileTemplatesAndReasons);
                }
            } catch (IOException e) {
                log.warn("Failed to create {}", jsonFileTemplatesAndReasons, e);
            }
        }
    }

    private static final String jsonFileTemplatesFinishReports = CONFIGURATION_FOLDER + File.separator + "templatesFinishReports.json";
    private static final String JSON_CONTENT_templatesFinishReports = """
            {
              "templatesEditReports": [
                {
                "setReportStatusTo": "COMPLETED",
                  "buttonName": "Completed - Standard",
                  "descriptionPublicNote": "Thank you for bringing this to our attention. Action was taken."
                },
                {
                "setReportStatusTo": "COMPLETED",
                  "buttonName": "Completed - Replay Desync",
                  "descriptionPublicNote": "Thank you for bringing this to our attention. Unfortunately, the game desyncs. I have made a note for the player in case it becomes a pattern."
                },
              {
                "setReportStatusTo": "COMPLETED",
                  "buttonName": "Completed - Smurf",
                  "descriptionPublicNote": "Thank you for bringing this to our attention. We will investigate."
                },
              {
                "setReportStatusTo": "COMPLETED",
                  "buttonName": "Completed - User Note",
                  "descriptionPublicNote": "Thank you for bringing this to our attention. I have noted this for the user in case of a pattern. Please report any further violations."
                },
              {
                "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - Leave-5-Minute-Rule",
                  "descriptionPublicNote": "Leaving a match is allowed after 5 minutes. I have noted this report for future reference in case a pattern emerges. Repeatedly leaving games may still violate other rules."
                },
                {
                "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - No Evidence",
                  "descriptionPublicNote": "No clear evidence was provided."
                },
              {
                "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - Replay Missing",
                  "descriptionPublicNote": "Please report again with the ReplayID."
                },
              {
                "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - Timecode Missing",
                  "descriptionPublicNote": "Please report again with the specific timecode of the violation."
                },
              {
                "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - Only Status",
                  "descriptionPublicNote": ""
                },
              {
                "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - Insufficient Information",
                  "descriptionPublicNote": "Please resubmit the report with clear details about the violation, including what happened, when it happened, and where it occurred."
                },
              {
                "setReportStatusTo": "PROCESSING",
                  "buttonName": "Processing - Review",
                  "descriptionPublicNote": "Thank you for your report. The moderation team will review the report and update its status once the review is complete."
                }
              ]
            }""";

    private void initTemplatesFinishReports(){
        File file = new File(jsonFileTemplatesFinishReports);
        if (!file.exists()) {
            try {
                Files.createDirectories(Paths.get(CONFIGURATION_FOLDER));
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(JSON_CONTENT_templatesFinishReports);
                    log.info("Created {}", jsonFileTemplatesFinishReports);
                }
            } catch (IOException e) {
                log.warn("Failed to create {}", jsonFileTemplatesFinishReports, e);
            }
        }
    }

    public void createTemplateGamingModeratorTask() {
        File fileCompleted = new File(CONFIGURATION_FOLDER + File.separator + "templateGamingModeratorTask.txt");
        if (!fileCompleted.exists()) {
            String contentCompleted = """
                    Gaming Moderator Task for FAForever.com

                    You are assessing a report submitted by %reporter% against %offenderNames%. Identify all chat messages that may violate the FAF rules. Translate any non-English insults into English where necessary, and assess their severity, context, and whether they warrant moderation action.""";
            try (FileWriter writer = new FileWriter(fileCompleted)) {
                writer.write(contentCompleted);
                log.info("Created {}", fileCompleted.getPath());
            } catch (IOException e) {
                log.error("Failed to create templateGamingModeratorTask.txt", e);
            }
        }
    }

    public void openFile(String fileName) throws IOException {
        openPath(new File(fileName));
    }

    public void onOpenAiPromptButton() throws IOException {
        openFile(CONFIGURATION_FOLDER + File.separator + "templateGamingModeratorTask.txt");
    }

    public void onChooseUpdateBackupFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Update Backup Folder");

        Path initialDirectory = resolveBackupFolderFieldPath();
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            directoryChooser.setInitialDirectory(initialDirectory.toFile());
        } else {
            Path defaultPath = Path.of(defaultUpdateBackupFolder);
            if (Files.isDirectory(defaultPath)) {
                directoryChooser.setInitialDirectory(defaultPath.toFile());
            }
        }

        File selectedDirectory = directoryChooser.showDialog(root.getScene().getWindow());
        if (selectedDirectory != null) {
            updateBackupFolderTextField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    public void onOpenUpdateBackupFolder() {
        try {
            Path folder = Optional.ofNullable(resolveBackupFolderFieldPath())
                    .orElse(Path.of(defaultUpdateBackupFolder));
            Files.createDirectories(folder);
            openPath(folder.toFile());
        } catch (IOException e) {
            log.error("Failed to open update backup folder", e);
        }
    }

    public void templatesAndReasonsReportButton() throws IOException {
        openFile(CONFIGURATION_FOLDER + File.separator +  "templatesAndReasons.json");

    }

    public void templatesFinishReportsButton() throws IOException {
        openFile(CONFIGURATION_FOLDER + File.separator +  "templatesFinishReports.json");

    }

    public void onBackupConfigurationFolderNow() {
        try {
            persistBackupFolderPreference(false);
            Path backupArchive = applicationUpdateService.createManualConfigurationBackupArchive();
            updateBackupFolderStatusLabel.setText("Backed up config folder to " + backupArchive);
        } catch (IOException e) {
            updateBackupFolderStatusLabel.setText("Failed to back up config folder: " + e.getMessage());
            log.error("Failed to back up configuration folder", e);
        }
    }

    public void onOpenReplayFolder() {
        try {
            replayStorageService.ensureReplayDirectoryExists();
            openPath(replayStorageService.resolveReplayDirectory().toFile());
        } catch (IOException e) {
            replayFolderStatusLabel.setText("Failed to open replay folder: " + e.getMessage());
            log.error("Failed to open replay folder", e);
        }
    }

    public void onRefreshReplayFolderInfo() {
        refreshReplayFolderInfo();
    }

    public void onPurgeOldReplayFiles() {
        try {
            ReplayStorageService.ReplayCleanupResult result = replayStorageService.purgeAllReplayFiles();
            replayFolderStatusLabel.setText(String.format(
                    Locale.ROOT,
                    "Purged %d replay files and freed %.2f MB.",
                    result.deletedFileCount(),
                    bytesToMegabytes(result.deletedBytes())
            ));
            refreshReplayFolderInfo();
        } catch (IOException e) {
            replayFolderStatusLabel.setText("Failed to purge replay files: " + e.getMessage());
            log.error("Failed to purge replay folder", e);
        }
    }

    private static void openPath(File path) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            new ProcessBuilder("cmd", "/c", "start", "", path.getAbsolutePath()).start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("open", path.getAbsolutePath()).start();
        } else {
            new ProcessBuilder("xdg-open", path.getAbsolutePath()).start();
        }
    }

    @Autowired
    private LocalPreferencesReaderWriter localPreferencesReaderWriter;

    public boolean onSave() {
        return saveSettings(true);
    }

    public boolean saveOnExit() {
        return saveSettings(false);
    }

    private boolean saveSettings(boolean applyStyleSheet) {
        log.info("Saving settings (applyStyleSheet={})", applyStyleSheet);

        localPreferences.getAutoLogin().setEnabled(rememberLoginCheckBox.isSelected());
        localPreferences.getUi().setDarkMode(darkModeCheckBox.isSelected());

        localPreferences.getTabSettings().setFetchBansOnStartupCheckBox(fetchBansOnStartupCheckBox.isSelected());
        localPreferences.getTabSettings().setAutoPurgeTempReplaysOlderThanOneDayCheckBox(
                autoPurgeTempReplaysOlderThanOneDayCheckBox.isSelected()
        );
        localPreferences.getTabSettings().setAutomaticConfigurationBackupsOnExitCheckBox(
                automaticConfigurationBackupsOnExitCheckBox.isSelected()
        );
        localPreferences.getTabIrcChat().setDebugTraffic(ircDebugTrafficCheckBox.isSelected());
        localPreferences.getTabReports().setEnableManualReplayLookupCheckBox(enableManualReplayLookupCheckBox.isSelected());
        localPreferences.getTabReports().setShowReportPlayerRoleLabelsCheckBox(showReportPlayerRoleLabelsCheckBox.isSelected());
        persistBackupFolderPreference(true);

        Tab selectedTab = defaultActiveTabComboBox.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            localPreferences.getUi().setStartUpTab(selectedTab.getId());
        }

        boolean saved = localPreferencesReaderWriter.write(localPreferences);
        if (!applyStyleSheet) {
            return saved;
        }

        Scene scene = root.getScene();
        String styleSheet = darkModeCheckBox.isSelected() ? "/style/main-dark.css" : "/style/main-light.css";

        scene.getStylesheets().clear();
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(styleSheet)).toExternalForm());
        return saved;
    }

    private Path resolveBackupFolderFieldPath() {
        String value = updateBackupFolderTextField.getText();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (Exception e) {
            log.debug("Ignoring invalid backup folder path: {}", value, e);
            return null;
        }
    }

    private void persistBackupFolderPreference(boolean refreshInfo) {
        String backupFolder = updateBackupFolderTextField.getText() == null ? "" : updateBackupFolderTextField.getText().trim();
        if (backupFolder.isBlank() || backupFolder.equals(defaultUpdateBackupFolder)) {
            localPreferences.getTabSettings().setUpdateBackupFolder("");
        } else {
            localPreferences.getTabSettings().setUpdateBackupFolder(Path.of(backupFolder).toAbsolutePath().normalize().toString());
        }
        if (refreshInfo) {
            refreshUpdateBackupFolderInfo();
        }
    }

    private void refreshUpdateBackupFolderInfo() {
        try {
            persistBackupFolderPreference(false);
            ApplicationUpdateService.BackupFolderStats stats = applicationUpdateService.describeBackupFolder();
            updateBackupFolderInfoLabel.setText(String.format(
                    Locale.ROOT,
                    "Stored backups: %.2f MB across %d files in %s",
                    bytesToMegabytes(stats.totalBytes()),
                    stats.fileCount(),
                    stats.directory()
            ));
            if (updateBackupFolderStatusLabel.getText() == null) {
                updateBackupFolderStatusLabel.setText("");
            }
        } catch (Exception e) {
            updateBackupFolderInfoLabel.setText("Stored backups: unavailable");
            updateBackupFolderStatusLabel.setText("Unable to inspect backup folder: " + e.getMessage());
            log.error("Failed to refresh update backup folder info", e);
        }
    }

    private double bytesToMegabytes(long bytes) {
        return bytes / (1024d * 1024d);
    }

    private void refreshReplayFolderInfo() {
        try {
            ReplayStorageService.ReplayFolderStats stats = replayStorageService.describeReplayFolder();
            replayFolderInfoLabel.setText(String.format(
                    Locale.ROOT,
                    "Replay folder: %s. Stored replays: %.2f MB across %d files.",
                    stats.directory(),
                    bytesToMegabytes(stats.totalBytes()),
                    stats.fileCount()
            ));
            if (replayFolderStatusLabel.getText() == null) {
                replayFolderStatusLabel.setText("");
            }
        } catch (IOException e) {
            replayFolderInfoLabel.setText("Replay folder: unavailable");
            replayFolderStatusLabel.setText("Unable to inspect replay folder: " + e.getMessage());
            log.error("Failed to refresh replay folder info", e);
        }
    }
}
