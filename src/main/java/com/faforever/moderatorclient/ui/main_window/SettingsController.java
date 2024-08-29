package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.ui.Controller;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.prefs.Preferences;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

@Component
@Slf4j
@RequiredArgsConstructor
public class SettingsController implements Controller<Region> {

    public VBox root;
    public TextField accountNameOrEmailTextField;
    public TextField accountPasswordField;
    @FXML
    public static Button saveAccountCredentialsButton;
    public TextField pathAccountFile;
    public Button blacklistedHashButton;
    public Button blacklistedIPButton;
    public Button blacklistedMemorySNButton;
    public Button blacklistedSNButton;
    public Button blacklistedUUIDButton;
    public Button blacklistedVolumeSNButton;
    public Button excludedItemsButton;
    public TextField genericJunkButton;
    public javafx.scene.control.Menu defaultStartingTabMenuBar;
    public CheckBox autoDiscardCheckBox;
    public CheckBox autoCompleteCheckBox;
    public Button openConfigurationFolderButton;

    private static final Path CONFIG_FILE_PATH = Paths.get(CONFIGURATION_FOLDER + File.separator + "config.properties");
    private final Properties properties = new Properties();

    @Setter
    public List<Tab> tabs;
    public Button openAiPromptButton;
    public Text excludedItemsLoadedText;
    public Text accountCredentialsText;

    private void setDefaultTab(Tab tab) {
        String tabName = tab.getId();
        if (CONFIG_FILE_PATH.toFile().exists()) {
            try (InputStream in = new FileInputStream(CONFIG_FILE_PATH.toFile())) {
                properties.load(in);
                properties.setProperty("user.choice.tab", tabName);
                properties.store(new FileOutputStream(CONFIG_FILE_PATH.toFile()), null);
                defaultStartingTabMenuBar.setText("New Starting Default Tab is " + tabName);
            } catch (IOException e) {log.error(e.getMessage());}
        }
    }

    public void loadConfigurationProperties() {
        try {
            properties.load(new FileInputStream(CONFIG_FILE_PATH.toFile()));
            String defaultStartingTab = properties.getProperty("user.choice.tab");
            defaultStartingTabMenuBar.setText("Default Starting Tab is " + defaultStartingTab);
        } catch (IOException e) {log.error(e.getMessage());}
    }

    @Override
    public VBox getRoot() {return root;}

    @FXML
    public void initialize() throws IOException {
        String[] credentials = SettingsController.loadCredentials();
        String username = credentials[0];

        if (username == null) {
            accountCredentialsText.setText("Account Credentials (no profile active)");
        } else {
            accountCredentialsText.setText("Account Credentials (profile " + username + " active)");
        }

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
        loadConfigurationProperties();
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
                "Bad/Illegal Username - Your login name was changed to NEW_USERNAME"
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
                  "description": "Thank you for bringing this to our attention. Action was taken."
                },
            	{
            	  "setReportStatusTo": "COMPLETED",
                  "buttonName": "Completed - Replay Desync",
                  "description": "Thank you for bringing this to our attention. Unfortunately, the game desyncs. I have made a note for the player in case it becomes a pattern. Please report any further violations."
                },
                {
            	  "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - Standard",
                  "description": "No additional information or proof was provided."
                },
            	{
            	  "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - Replay Missing",
                  "description": "Please report again with the ReplayID."
                },
            	{
            	  "setReportStatusTo": "DISCARDED",
                  "buttonName": "Discarded - Timecode Missing",
                  "description": "Please report again with the specific timecode of the violation."
                },
            	{
            	  "setReportStatusTo": "PROCESSING",
                  "buttonName": "Processing - Investigation",
                  "description": "Thank you for bringing this to our attention. We are investigating the case and it may take some time, until we set it to 'completed'."
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


    public void initTabStuff() {
        for (Tab tab : tabs) {
            MenuItem menuItem = new MenuItem(tab.getText());
            menuItem.setId(tab.getId());
            menuItem.setOnAction((event) -> {
                MenuItem menItem = (MenuItem) event.getSource();
                String string = menItem.getId();
                for (Tab t : tabs) {
                    if (!t.getId().equals(string)) continue;
                    setDefaultTab(t);
                    break;
                }
            });
            defaultStartingTabMenuBar.getItems().add(menuItem);
        }
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

    public void onSaveAccountButton() {
        String accountNameOrEmail = accountNameOrEmailTextField.getText();
        String accountPassword = accountPasswordField.getText();
        accountCredentialsText.setText("Account Credentials (profile " + accountNameOrEmail + " active)");
        accountNameOrEmailTextField.setText("");
        accountPasswordField.setText("");

        saveCredentials(accountNameOrEmail, accountPassword);
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
        try {
            Desktop.getDesktop().open(folder);
        } catch (IOException e) {
            log.error(e.getMessage());
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

    public void onOpenAiPromptButton() throws IOException {
        openFile(CONFIGURATION_FOLDER + File.separator + "templateGamingModeratorTask.txt");
    }

    public void templatesAndReasonsReportButton() throws IOException {
        openFile(CONFIGURATION_FOLDER + File.separator +  "templatesAndReasons.json");

    }

    public void templatesFinishReportsButton() throws IOException {
        openFile(CONFIGURATION_FOLDER + File.separator +  "templatesFinishReports.json");

    }

    private static final String PREF_NODE = "com.faforever.moderatorclient.credentials";
    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";

    public static void saveCredentials(String username, String password) {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        prefs.put(USERNAME_KEY, username);
        prefs.put(PASSWORD_KEY, password);
    }

    public static String[] loadCredentials() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        String username = prefs.get(USERNAME_KEY, null);
        String password = prefs.get(PASSWORD_KEY, null);

        return new String[]{username, password};
    }
}