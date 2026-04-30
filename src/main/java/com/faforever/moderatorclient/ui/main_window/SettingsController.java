package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.config.local.LocalPreferencesReaderWriter;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.MainController;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
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
import java.util.Objects;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

@Component
@Slf4j
@RequiredArgsConstructor
public class SettingsController implements Controller<Pane> {
    private final LocalPreferences localPreferences;
    private final MainController mainController;

    public VBox root;
    @FXML
    public CheckBox rememberLoginCheckBox;
    @FXML
    public CheckBox darkModeCheckBox;
    @FXML
    public ComboBox<Tab> defaultActiveTabComboBox;
    @FXML
    public Button openConfigurationFolderButton;

    @FXML
    public Button openAiPromptButton;

    @FXML
    public ComboBox<String> browserComboBox;
    @FXML
    public CheckBox fetchBansOnStartupCheckBox;


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


    private static final String jsonFileTemplatesAndReasons = CONFIGURATION_FOLDER + File.separator + "templatesAndReasons.json";
    private static final String JSON_CONTENT_templatesAndReasons = """
            {
              "templates": [
                {
                  "name": "Standard Ban",
                  "format": "{reportIds}\\n\\nDAY_NUMBER day ban - ReplayID {gameIds} - {reason}"
                },
                {
                  "name": "Your Custom Ban",
                  "format": "{reportIds}\\n\\nDAY_NUMBER day ban - ReplayID {gameIds} - {reason}"
                }
              ],
              "reasons": [
                "Offensive Language",
                "Reclaiming Friendly Units",
                "Attacking Friendly Units",
                "CTRL+K All Units in Fullshare Game Mode",
                "Harassment via Private Chat",
                "Offensive Kick Messages",
                "Racism",
                "Offensive Game Titles",
                "Leaving on Own Terms/Game Ruining",
                "Abuse of Exploits",
                "Bad/Illegal Username"
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
        } else {
            log.info(jsonFileTemplatesAndReasons +" already exists.");
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
                  "descriptionPublicNote": "Thank you for bringing this to our attention. Unfortunately, the game desyncs. We have made a note for the player in case it becomes a pattern. Please report any further violations."
                },
                {
            	  "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - Standard",
                  "descriptionPublicNote": "No additional information or proof was provided."
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
            	  "setReportStatusTo": "PROCESSING",
                  "buttonName": "Processing - Investigation",
                  "descriptionPublicNote": "Thank you for bringing this to our attention. We are investigating the case and it may take some time, until we set it to 'completed'."
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
        } else {
            log.info("{} already exists.", jsonFileTemplatesFinishReports);
        }
    }

    public void createTemplateGamingModeratorTask() {
        File fileCompleted = new File(CONFIGURATION_FOLDER + File.separator + "templateGamingModeratorTask.txt");
        if (!fileCompleted.exists()) {
            String contentCompleted = """
                    AI-Prompt: Gaming Moderator Task
                    Reported Chat Log Assessing report from %reporter% against offender %offenderNames%:
                    Itemize all instances of speech by %offenderNames%. Translate to English where necessary.""";
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

    public void onOpenConfigurationFolder() {
        File folder = new File(CONFIGURATION_FOLDER);
        if (folder.exists() && folder.isDirectory()) {
            try {
                openPath(folder);
            } catch (IOException e) {
                log.error("Failed to open configuration folder", e);
            }
        } else {
            log.warn("Configuration folder does not exist or is invalid: {}", folder.getAbsolutePath());
        }
    }

    public void onOpenAiPromptButton() throws IOException {
        openFile(CONFIGURATION_FOLDER + File.separator + "templateGamingModeratorTask.txt");
    }

    public void templatesAndReasonsReportButton() throws IOException {
        openFile(CONFIGURATION_FOLDER + File.separator +  "templatesAndReasons.json");

    }

    public void templatesFinishReportsButton() throws IOException {
        openFile(CONFIGURATION_FOLDER + File.separator +  "templatesFinishReports.json");

    }

    public void onOpenPathToUserSettings() {
        String path = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming" + File.separator + "Mordor";
        File directory = new File(path);

        if (directory.exists() && directory.isDirectory()) {
            try {
                openPath(directory);
            } catch (IOException e) {
                log.error("Failed to open directory {}", path, e);
            }
        } else {
            log.info("Directory does not exist: {}", path);
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

    public void onSave() {
        log.info("onSave from SettingsController.java");

        // Save preferences from UI
        localPreferences.getAutoLogin().setEnabled(rememberLoginCheckBox.isSelected());
        localPreferences.getUi().setDarkMode(darkModeCheckBox.isSelected());

        localPreferences.getTabSettings().setFetchBansOnStartupCheckBox(fetchBansOnStartupCheckBox.isSelected());

        Tab selectedTab = defaultActiveTabComboBox.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            localPreferences.getUi().setStartUpTab(selectedTab.getId());
        }

        localPreferencesReaderWriter.write(localPreferences);
        Scene scene = root.getScene();
        String styleSheet = darkModeCheckBox.isSelected() ? "/style/main-dark.css" : "/style/main-light.css";

        scene.getStylesheets().clear();
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(styleSheet)).toExternalForm());
    }
}
