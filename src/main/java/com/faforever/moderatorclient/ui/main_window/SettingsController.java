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
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

@Component
@Slf4j
@RequiredArgsConstructor
public class SettingsController implements Controller<Region> {

    public VBox root;
    public TextField accountNameOrEmailTextField;
    public TextField accountPasswordTextField;
    public Button saveAccountButton;
    public TextField pathAccountFile;
    public Button BlacklistedHash;
    public Button BlacklistedIP;
    public Button BlacklistedMemorySN;
    public Button BlacklistedSN;
    public Button BlacklistedUUID;
    public Button BlacklistedVolumeSN;
    public Button ExcludedItems;
    public Button templateCompletedButton;
    public Button templateDiscardedButton;
    public Button templateReportButton;
    public TextField genericJunk;
    public MenuItem optionUserManagementTab;
    public MenuItem optionReportTab;
    public MenuItem optionRecentActivityTab;
    public javafx.scene.control.Menu defaultStartingTabMenuBar;
    public CheckBox autoDiscard;
    public CheckBox autoComplete;
    public Button templateButtonTemporaryBanButton;
    public Button templateButtonPermanentBanButton;
    public Button saveButton;

    private void setDefaultTab(String tabName) {
        Properties config = new Properties();
        try {
            config.load(new FileInputStream("config.properties"));
            config.setProperty("user.choice.tab", tabName);
            config.store(new FileOutputStream("config.properties"), null);
            defaultStartingTabMenuBar.setText("current default is " + tabName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handleOptionUserManagementTabClicked() {
        setDefaultTab("userManagementTab");
    }

    public void handleOptionReportTabClicked() {
        setDefaultTab("reportTab");
    }

    public void handleOptionRecentActivityTabClicked() {
        setDefaultTab("recentActivityTab");
    }

    @Override
    public VBox getRoot() {return root;}

    @FXML
    public void initialize() throws IOException {
        String[] blacklistedFiles = { "BlacklistedHash", "BlacklistedIP", "BlacklistedMemorySN",
                "BlacklistedSN", "BlacklistedUUID", "BlacklistedVolumeSN", "ExcludedItems" };

        // create default blacklisted files if they do not exist
        for (String file : blacklistedFiles) {
            File f = new File(file + ".txt");
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
        pathAccountFile.setText(homeDirectory);
        String filePath = homeDirectory + File.separator + "account_credentials_mordor.txt";
        File f = new File(filePath);

        if (f.exists() && !f.isDirectory()) {
            Path pathCredentialsFile = Path.of(filePath);

            try {
                List<String> credentials = Files.readAllLines(pathCredentialsFile);
                String nameOrEmail = credentials.get(0);
                String password = credentials.get(1);
                accountNameOrEmailTextField.setText(nameOrEmail);
                accountPasswordTextField.setText(password);
            } catch (Exception e) {
                log.debug("Error reading account credentials: " + e.getMessage());
            }
        }

        try {
            Properties config = new Properties();
            config.load(new FileInputStream("config.properties"));
            autoDiscard.setSelected(Boolean.parseBoolean(config.getProperty("autoDiscard", "false")));
            autoComplete.setSelected(Boolean.parseBoolean(config.getProperty("autoComplete", "false")));
        } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }

        public void saveAccount() {
        String accountNameOrEmail = accountNameOrEmailTextField.getText();
        String accountPassword = accountPasswordTextField.getText();
        String data = accountNameOrEmail + "\n" + accountPassword;

        String homeDirectory = System.getProperty("user.home");
        String filePath = homeDirectory + File.separator + "account_credentials_mordor.txt";

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

    public void SaveAccountButton() {
        saveAccount();
    }

    public void openFile(String fileName) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("Notepad.exe", fileName);
        pb.start();
    }

    public void onTemplateButtonPermanentBanButton() throws IOException {openFile("templateButtonPermanentBan.txt");}
    public void onTemplateButtonTemporaryBanButton() throws IOException {openFile("templateButtonTemporaryBan.txt");}

    public void BlacklistedHash() throws IOException {openFile("BlacklistedHash.txt");}

    public void BlacklistedIP() throws IOException {openFile("BlacklistedIP.txt");}

    public void BlacklistedSN() throws IOException {openFile("BlacklistedSN.txt");}

    public void BlacklistedUUID() throws IOException {openFile("BlacklistedUUID.txt");}

    public void BlacklistedVolumeSN() throws IOException {openFile("BlacklistedVolumeSN.txt");}

    public void BlacklistedMemorySN() throws IOException {openFile("BlacklistedMemorySN.txt");}

    public void ExcludedItems() throws IOException {openFile("excludedItems.txt");}

    public void TemplateCompletedButton() throws IOException {openFile("TemplateCompleted.txt");}

    public void TemplateDiscardedButton() throws IOException {openFile("TemplateDiscarded.txt");}

    public void TemplateReportButton() throws IOException {openFile("TemplateReport.txt");}

    public void handleSaveButtonForCheckBox() {
        Properties config = new Properties();
        try {
            config.load(new FileInputStream("config.properties"));
            config.setProperty("autoDiscard", Boolean.toString(autoDiscard.isSelected()));
            config.setProperty("autoComplete", Boolean.toString(autoComplete.isSelected()));
            config.store(new FileOutputStream("config.properties"), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
