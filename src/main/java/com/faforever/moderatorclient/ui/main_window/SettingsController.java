package com.faforever.moderatorclient.ui.main_window;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.faforever.moderatorclient.ui.*;
import javafx.fxml.FXML;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

@Component
@Slf4j
@RequiredArgsConstructor
public class SettingsController implements Controller<Region> {

    public VBox root;
    public TextField accountNameOrEmailTextField;
    public TextField accountPasswordField;
    public Button saveAccountButton;
    public TextField pathAccountFile;
    public Button blacklistedHashButton;
    public Button blacklistedIPButton;
    public Button blacklistedMemorySNButton;
    public Button blacklistedSNButton;
    public Button blacklistedUUIDButton;
    public Button blacklistedVolumeSNButton;
    public Button excludedItemsButton;
    public Button templateCompletedButton;
    public Button templateDiscardedButton;
    public Button templateReasonsCheckBoxButton;
    public TextField genericJunkButton;
    public MenuItem optionUserManagementTab;
    public MenuItem optionReportTab;
    public MenuItem optionRecentActivityTab;
    public javafx.scene.control.Menu defaultStartingTabMenuBar;
    public CheckBox autoDiscardCheckBox;
    public CheckBox autoCompleteCheckBox;
    public Button templateButtonPermanentBanButton;
    public Button saveButton;
    public Button openConfigurationFolderButton;

    private void setDefaultTab(String tabName) {
        Properties config = new Properties();
        try {
            config.load(new FileInputStream(CONFIGURATION_FOLDER + File.separator + "config.properties"));
            config.setProperty("user.choice.tab", tabName);
            config.store(new FileOutputStream(CONFIGURATION_FOLDER + File.separator + "config.properties"), null);
            defaultStartingTabMenuBar.setText("current default is " + tabName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onOptionUserManagementTabClicked() {
        setDefaultTab("userManagementTab");
    }

    public void handleOptionReportTabClicked() {
        setDefaultTab("reportTab");
    }

    public void onOptionRecentActivityTabClicked() {
        setDefaultTab("recentActivityTab");
    }

    @Override
    public VBox getRoot() {return root;}

    @FXML
    public void initialize() throws IOException {
        String[] blacklistedFiles = { "blacklistedHash", "blacklistedIP", "blacklistedMemorySN",
                "blacklistedSN", "blacklistedUUID", "blacklistedVolumeSN", "excludedItems" };

        // create default blacklisted files if they do not exist
        for (String file : blacklistedFiles) {
            File f = new File(CONFIGURATION_FOLDER + File.separator + file + ".txt");
            try {
                if (!f.exists()) {
                    if (f.createNewFile()) {
                        log.info("[info] " + f + " was successfully created.");
                    } else {
                        log.info("[info] " + f + " already exists.");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String homeDirectory = System.getProperty("user.home");
        String filePath = homeDirectory + File.separator + "account_credentials_mordor.txt";
        pathAccountFile.setText(filePath);
        File f = new File(filePath);

        if (f.exists() && !f.isDirectory()) {
            Path pathCredentialsFile = Path.of(filePath);

            try {
                List<String> credentials = Files.readAllLines(pathCredentialsFile);
                String nameOrEmail = credentials.get(0);
                String password = credentials.get(1);
                accountNameOrEmailTextField.setText(nameOrEmail);
                accountPasswordField.setText(password);
            } catch (Exception e) {
                log.debug("Error reading account credentials: " + e.getMessage());
            }
        }
        // Load configuration properties
        try {
            Properties config = new Properties();
            config.load(new FileInputStream(CONFIGURATION_FOLDER + File.separator + "config.properties"));
            autoDiscardCheckBox.setSelected(Boolean.parseBoolean(config.getProperty("autoDiscardCheckBox", "false")));
            autoCompleteCheckBox.setSelected(Boolean.parseBoolean(config.getProperty("autoCompleteCheckBox", "false")));
            String defaultStartingTab = config.getProperty("user.choice.tab", "reportTab");
            defaultStartingTabMenuBar.setText("current default is " + defaultStartingTab);
        } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }

        public void saveAccount() {
        String accountNameOrEmail = accountNameOrEmailTextField.getText();
        String accountPassword = accountPasswordField.getText();
        String data = accountNameOrEmail + "\n" + accountPassword;

        String filePath = System.getProperty("user.home") + File.separator + "account_credentials_mordor.txt";

        try {
            FileWriter fw = new FileWriter(filePath, false);
            fw.write(data);
            fw.flush();
            fw.close();
            saveAccountButton.setText("Credentials were saved.");
        } catch (IOException e) {
            log.error("Error saving account credentials: " + e.getMessage());
        }
    }

    public void onSaveAccountButton() {
        saveAccount();
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

    public void onTemplateButtonPermanentBanButton() throws IOException {openFile(CONFIGURATION_FOLDER + "/templateButtonPermanentBan.txt");}
    public void onBlacklistedHash() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedHash.txt");}
    public void onBlacklistedIP() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedIP.txt");}
    public void onBlacklistedSN() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedSN.txt");}
    public void onBlacklistedUUID() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedUUID.txt");}
    public void onBlacklistedVolumeSN() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedVolumeSN.txt");}
    public void onBlacklistedMemorySN() throws IOException {openFile(CONFIGURATION_FOLDER + "/blacklistedMemorySN.txt");}
    public void onExcludedItems() throws IOException {openFile(CONFIGURATION_FOLDER + "/excludedItems.txt");}
    public void onTemplateCompletedButton() throws IOException {openFile(CONFIGURATION_FOLDER + "/templateCompleted.txt");}
    public void onTemplateDiscardedButton() throws IOException {openFile(CONFIGURATION_FOLDER + "/templateDiscarded.txt");}
    public void onReasonsTemplateCheckBoxButton() throws IOException {openFile(CONFIGURATION_FOLDER + "/templateReasonsCheckBox.txt");}

    public void onSaveButtonForCheckBox() {
        Properties config = new Properties();
        try {
            config.load(new FileInputStream(CONFIGURATION_FOLDER + File.separator + "config.properties"));
            config.setProperty("autoDiscardCheckBox", Boolean.toString(autoDiscardCheckBox.isSelected()));
            config.setProperty("autoCompleteCheckBox", Boolean.toString(autoCompleteCheckBox.isSelected()));
            config.store(new FileOutputStream(CONFIGURATION_FOLDER + File.separator + "config.properties"), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onOpenConfigurationFolder() {
        File folder = new File(CONFIGURATION_FOLDER);
        try {
            Desktop.getDesktop().open(folder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
