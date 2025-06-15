package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.MainController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import java.awt.*;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class SettingsController implements Controller<Pane> {
    private final LocalPreferences localPreferences;
    private final MainController mainController;

    public VBox root;
    public CheckBox rememberLoginCheckBox;
    public CheckBox darkModeCheckBox;
    public ComboBox<Tab> defaultActiveTabComboBox;
    public Button blacklistedHashButton;
    public Button blacklistedIPButton;
    public Button blacklistedMemorySNButton;
    public Button blacklistedSNButton;
    public Button blacklistedUUIDButton;
    public Button blacklistedVolumeSNButton;
    public Button excludedItemsButton;
    public TextField genericJunkButton;
    public Button openConfigurationFolderButton;

    @Setter
    public Button openAiPromptButton;
    public Text excludedItemsLoadedText;

    @FXML
    public ComboBox<String> browserComboBox;

    @Override
    public VBox getRoot() {
        return root;
    }

    @FXML
    public void initialize() throws IOException {
        browserComboBox.setItems(FXCollections.observableArrayList("Firefox", "Chrome", "Opera", "Microsoft Edge"));

        mainController.getRoot().getTabs().forEach(t -> System.out.println("Tab ID: " + t.getId()));
        if (defaultActiveTabComboBox.getSelectionModel().isEmpty() && !defaultActiveTabComboBox.getItems().isEmpty()) {
            defaultActiveTabComboBox.getSelectionModel().selectFirst();
        }

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

        rememberLoginCheckBox.setSelected(localPreferences.getAutoLogin().isEnabled());
        darkModeCheckBox.setSelected(localPreferences.getUi().isDarkMode());

        mainController.getRoot().getTabs().forEach(tab -> {
            defaultActiveTabComboBox.getItems().add(tab);
            if (Objects.equals(tab.getId(), localPreferences.getUi().getStartUpTab())) {
                defaultActiveTabComboBox.getSelectionModel().select(tab);
            }
        });

        CreateTableColumnsWidthSettingsJSON();

        log.debug("Initializing blacklistedFiles");
        String[] blacklistedFiles = { "blacklistedHash", "blacklistedIP", "blacklistedMemorySN",
                "blacklistedSN", "blacklistedUUID", "blacklistedVolumeSN", "excludedItems" };

        // create default blacklisted files if they do not exist
        File directory = new File(CONFIGURATION_FOLDER);
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                log.debug("Configuration directory created: {}", directory.getAbsolutePath());
            } else {
                log.error("Failed to create configuration directory: {}", directory.getAbsolutePath());
            }
        } else {
            log.debug("Configuration directory already exists: {}", directory.getAbsolutePath());
        }

        for (String file : blacklistedFiles) {
            File f = new File(CONFIGURATION_FOLDER + File.separator + file + ".txt");
            try {
                if (!f.exists()) {
                    if (f.createNewFile()) {
                        log.info("[info] {} was successfully created.", f);
                    } else {
                        log.info("[info] {} already exists.", f);
                    }
                }
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        }

        int lineCount = countLinesInFile(new File(CONFIGURATION_FOLDER + File.separator + "excludedItems.txt"));
        excludedItemsLoadedText.setText("Total Excluded Items Loaded " + lineCount + ":");

        File configFile = new File(CONFIGURATION_FOLDER + File.separator + "config.properties");
        try {
            configFile.createNewFile();
            log.debug("{} was created.", configFile);
        } catch (IOException e) {
            log.error("[error] Failed to create {}", configFile, e);
        }

        initTemplatesAndReasons();
        initTemplatesFinishReports();
        createTemplateGamingModeratorTask();
    }

    public void onSave() throws IOException {
        log.info("Saving settings");

        Tab selectedTab = defaultActiveTabComboBox.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            localPreferences.getUi().setStartUpTab(selectedTab.getId());
        } else {
            log.warn("No startup tab selected; skipping saving startUpTab.");
        }

        localPreferences.getAutoLogin().setEnabled(rememberLoginCheckBox.isSelected());
        localPreferences.getUi().setDarkMode(darkModeCheckBox.isSelected());
        localPreferences.getUi().setStartUpTab(defaultActiveTabComboBox.getSelectionModel().getSelectedItem().getId());

        Scene scene = root.getScene();
        String styleSheet = "/style/main-light.css";
        if (darkModeCheckBox.isSelected()) {
            styleSheet = "/style/main-dark.css";
        }

        scene.getStylesheets().clear();
        scene.getStylesheets().add(getClass().getResource(styleSheet).toExternalForm());
    }


    public void createTemplateGamingModeratorTask() {
        try {
            File fileCompleted = new File(CONFIGURATION_FOLDER + File.separator + "templateGamingModeratorTask.txt");

            if (!fileCompleted.exists()) {
                String contentCompleted = """
                        AI-Prompt: Gaming Moderator Task
                        Reported Chat Log Assessing report from %reporter% against offender %offenderNames%:
                        Itemize all instances of speech by %offenderNames%. Translate to English where necessary.""";
                FileWriter writer = new FileWriter(fileCompleted);
                writer.write(contentCompleted);
                writer.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int countLinesInFile(File file) throws IOException {
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) {
                lines++;
            }
        } catch (IOException e) {
            throw new IOException();
        }
        return lines;
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
                    System.out.println("File created and content written successfully.");
                }
            } catch (IOException e) {
                log.warn(String.valueOf(e));
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
                  "descriptionPublicNote": "Thank you for bringing this to our attention. Unfortunately, the game desyncs. I have made a note for the player in case it becomes a pattern. Please report any further violations."
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
                    System.out.println("File created and content written successfully.");
                }
            } catch (IOException e) {
                log.warn(String.valueOf(e));
            }
        } else {
            log.info(jsonFileTemplatesFinishReports +" already exists.");
        }
    }

    public void openFile(String fileName) throws IOException {
        Path notepadPlusPlus = Paths.get("C:\\Program Files\\Notepad++\\notepad++.exe");
        Path notepad = Paths.get("C:\\Windows\\System32\\notepad.exe");

        if (Files.exists(notepadPlusPlus)) {
            ProcessBuilder pb = new ProcessBuilder(notepadPlusPlus.toString(), fileName);
            pb.start();
        } else {
            ProcessBuilder pb = new ProcessBuilder(notepad.toString(), fileName);
            pb.start();
        }
    }

    public void onBlacklistedHash() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedHash.txt");}
    public void onBlacklistedIP() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedIP.txt");}
    public void onBlacklistedSN() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedSN.txt");}
    public void onBlacklistedUUID() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedUUID.txt");}
    public void onBlacklistedVolumeSN() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedVolumeSN.txt");}
    public void onBlacklistedMemorySN() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedMemorySN.txt");}
    public void onExcludedItems() throws IOException {openFile(CONFIGURATION_FOLDER + "/excludedItems.txt");}

    public void onOpenConfigurationFolder() {
        File folder = new File(CONFIGURATION_FOLDER);

        if (Desktop.isDesktopSupported() && !GraphicsEnvironment.isHeadless()) {
            try {
                Desktop.getDesktop().open(folder);
            } catch (IOException e) {
                log.error("Failed to open configuration folder: {}", e.getMessage(), e);
                showErrorWithCopy("Unable to open configuration folder.", folder.getAbsolutePath());
            }
        } else {
            log.warn("Cannot open configuration folder: Desktop is not supported or environment is headless.");
            showErrorWithCopy("Desktop not supported or environment is headless.", folder.getAbsolutePath());
        }
    }

    private void showErrorWithCopy(String title, String folderPath) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(folderPath);

            ButtonType copyButton = new ButtonType("Copy Path", ButtonBar.ButtonData.OK_DONE);
            ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(copyButton, closeButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == copyButton) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(folderPath);
                clipboard.setContent(content);
            }
        });
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

    public String getSelectedBrowser() {
        String selectedBrowser = browserComboBox.getValue();
        log.debug("Selected Browser: {}", selectedBrowser);
        return selectedBrowser;
    }

    public void setSelectedBrowser(String selectedBrowser) {
        if (browserComboBox == null) {
            log.error("Warning: browserComboBox is not initialized.");
            return;
        }

        if (selectedBrowser != null) {
            browserComboBox.setValue(selectedBrowser);
            log.debug("value set for combobox " +  selectedBrowser);
        }
    }

    public static void CreateTableColumnsWidthSettingsJSON() {
        String filePath = "ConfigurationModerationToolFAF/TableColumnsWidthSettings.json";

        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode data = mapper.createObjectNode();

            if (!file.exists()) {
                ArrayNode columns = mapper.createArrayNode();

                columns.add(mapper.createObjectNode()
                        .put("name", "reporterColumn")
                        .put("prefWidth", 90));

                columns.add(mapper.createObjectNode()
                        .put("name", "reportedUsersColumn")
                        .put("prefWidth", 120));

                columns.add(mapper.createObjectNode()
                        .put("name", "reportDescriptionColumn")
                        .put("prefWidth", 1400));

                columns.add(mapper.createObjectNode()
                        .put("name", "incidentTimeCodeColumn")
                        .put("prefWidth", 90));

                columns.add(mapper.createObjectNode()
                        .put("name", "privateNoteColumn")
                        .put("prefWidth", 120));

                columns.add(mapper.createObjectNode()
                        .put("name", "moderatorPrivateNoticeColumn")
                        .put("prefWidth", 120));

                columns.add(mapper.createObjectNode()
                        .put("name", "lastModeratorColumn")
                        .put("prefWidth", 120));

                columns.add(mapper.createObjectNode()
                        .put("name", "createTimeColumn")
                        .put("prefWidth", 120));

                data.set("columns", columns);

                FileWriter writer = new FileWriter(file);
                writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
                writer.close();
                log.debug("TableColumnsWidthSettingsJSON was created.");
            } else {
                log.debug("TableColumnsWidthSettingsJSON already exists.");
            }

        } catch (IOException e) {
            log.error("Error creating file: {}", e.getMessage());
        }
    }
}
