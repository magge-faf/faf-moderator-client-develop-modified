package com.faforever.moderatorclient.ui.main_window;

import javafx.scene.control.Button;
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
    public javafx.scene.control.Menu menuBarText;

    private static final String USER_CHOICE_FILENAME = "userChoiceDefaultTab.txt";

    public void handleOptionUserManagementTabClicked() {
        try {
            File file = new File(USER_CHOICE_FILENAME);
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write("userManagementTab");
            fileWriter.flush();
            fileWriter.close();
            menuBarText.setText("current default is userManagementTab");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handleOptionReportTabClicked() {
        try {
            File file = new File(USER_CHOICE_FILENAME);
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write("reportTab");
            fileWriter.flush();
            fileWriter.close();
            menuBarText.setText("current default is reportTab");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handleOptionRecentActivityTabClicked() {
        try {
            File file = new File(USER_CHOICE_FILENAME);
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write("recentActivityTab");
            fileWriter.flush();
            fileWriter.close();
            menuBarText.setText("current default is recentActivityTab");
        } catch (IOException e) {
            e.printStackTrace();
        }
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

        File f = new File("account_credentials.txt");
        if (f.exists() && !f.isDirectory()) {
            Path pathCredentialsFile = Path.of("account_credentials.txt");
            pathAccountFile.setText(String.valueOf(pathCredentialsFile.toAbsolutePath()));
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
    }

    public void saveAccount() {
        String accountNameOrEmail = accountNameOrEmailTextField.getText();
        String accountPassword = accountPasswordTextField.getText();
        String data = accountNameOrEmail + "\n" + accountPassword;
        try {
            FileWriter fw = new FileWriter("account_credentials.txt", false);
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
}
