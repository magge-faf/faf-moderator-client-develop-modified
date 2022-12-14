package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.ui.moderation_reports.ModerationReportController;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.faforever.moderatorclient.ui.*;
import javafx.fxml.FXML;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
@Slf4j
@RequiredArgsConstructor
public class SettingsController implements Controller<Region> {

    public VBox root;
    public TextField AccountNameOrEmailTextField;
    public TextField AccountPasswordTextField;
    public Button SaveAccountButton;
    public TextField PathAccountFile;
    public Button BlacklistedHash;
    public Button BlacklistedIP;
    public Button BlacklistedMemorySN;
    public Button BlacklistedSN;
    public Button BlacklistedUUID;
    public Button BlacklistedVolumeSN;
    public Button ExcludedItems;
    public TextField AllModeratorStatsTextField;
    public Button TemplateCompletedButton;
    public Button TemplateDiscardedButton;
    public Button TemplateReportButton;
    public TextArea MostReportsOffendersTextArea;
    public Button LoadAllReportsAndModeratorStatsAndTopOffendersButton;
    public TextField GenericJunk;

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
                // Print the stack trace for the exception
                e.printStackTrace();
                // You can also log the exception here, or handle it in some other way
            }
        }


        File f = new File("account_credentials.txt");
        if (f.exists() && !f.isDirectory()) {
            Path pathCredentialsFile = Path.of("account_credentials.txt");
            PathAccountFile.setText(String.valueOf(pathCredentialsFile.toAbsolutePath()));
            try {
                List<String> credentials = Files.readAllLines(pathCredentialsFile);
                String nameOrEmail = credentials.get(0);
                String password = credentials.get(1);
                AccountNameOrEmailTextField.setText(nameOrEmail);
                AccountPasswordTextField.setText(password);
            } catch (Exception e) {
                log.debug("Error reading account credentials: " + e.getMessage());
            }
        }
    }

    public void saveAccount() {
        String accountNameOrEmail = AccountNameOrEmailTextField.getText();
        String accountPassword = AccountPasswordTextField.getText();
        String data = accountNameOrEmail + "\n" + accountPassword;
        try {
            FileWriter fw = new FileWriter("account_credentials.txt", false);
            fw.write(data);
            fw.flush();
            fw.close();
            SaveAccountButton.setText("Credentials were saved.");
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

    public void BlacklistedHash() throws IOException {
        openFile("BlacklistedHash.txt");
    }

    public void BlacklistedIP() throws IOException {
        openFile("BlacklistedIP.txt");
    }

    public void BlacklistedSN() throws IOException {
        openFile("BlacklistedSN.txt");
    }

    public void BlacklistedUUID() throws IOException {
        openFile("BlacklistedUUID.txt");
    }

    public void BlacklistedVolumeSN() throws IOException {
        openFile("BlacklistedVolumeSN.txt");
    }

    public void BlacklistedMemorySN() throws IOException {
        openFile("BlacklistedMemorySN.txt");
    }

    public void ExcludedItems() throws IOException {
        openFile("excludedItems.txt");
    }

    public void TemplateCompletedButton() throws IOException {
        openFile("TemplateCompleted.txt");
    }

    public void TemplateDiscardedButton() throws IOException {
        openFile("TemplateDiscarded.txt");
    }

    public void TemplateReportButton() throws IOException {
        openFile("TemplateReport.txt");
    }

    int extractInt(String s) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        } else {
            return 0;
        }
    }

    public void LoadAllReportsAndModeratorStatsAndTopOffendersButton() {
        try {
            String allReportsString = String.valueOf(ModerationReportController.GlobalConstants.allReports);
            allReportsString = allReportsString.replaceAll("\\[.*?]",""); // remove []
            String[] dataList = allReportsString.split(",");
            List<String> Moderator = new ArrayList<>(Collections.singletonList(""));

            for (String item : dataList) {
                item = Arrays.toString(item.split(":"));
                Moderator.add(Arrays.toString(new String[]{item.split(",")[1]}));
            }

            Set<String> unique = new HashSet<>(Moderator);
            List<String> totalProcessedReportsRaw = new ArrayList<>();
            List<String> totalProcessedReportsProcessed = new ArrayList<>();

            for (String key : unique) {
                if (!key.equals("")){
                    totalProcessedReportsRaw.add(key + ": " + Collections.frequency(Moderator, key));
                }
            }

            for (String item : totalProcessedReportsRaw) {
                item = item.replaceAll("\\[", "").replaceAll("]", "").replaceAll(" ", "");
                totalProcessedReportsProcessed.add(item);
            }

            totalProcessedReportsProcessed.sort((o1, o2) -> {
                // Extract the number of reports processed from each string
                int num1 = extractInt(o1);
                int num2 = extractInt(o2);
                // Compare the numbers
                return num1 - num2;
            });


            List<String> finalList = new ArrayList<>();

            for (String item : totalProcessedReportsProcessed){
                if (!item.contains("DISCARDED")){  // ignore legacy reports which has DISCARDED as moderator name
                    finalList.add(item);
                }
            }

            List<?> shallowCopy = finalList.subList(0, finalList.size());
            Collections.reverse(shallowCopy);

            AllModeratorStatsTextField.setText(String.valueOf(finalList));

            String AwaitingReportsTotalTextAreaString = String.valueOf(ModerationReportController.GlobalConstants.AwaitingReportsTotalTextArea);

            List<String> allOffendersListGlobal = ModerationReportController.GlobalConstants.allOffenders;
            List<String> allRUOffendersList = ModerationReportController.GlobalConstants.allRUOffenders;

            allOffendersListGlobal.removeAll(allRUOffendersList);
            Map<String, Integer> offenderCounts = new HashMap<>();
            for (String offender : allOffendersListGlobal) {
                offenderCounts.put(offender, offenderCounts.getOrDefault(offender, 0) + 1);
            }

            for (Map.Entry<String, Integer> entry : offenderCounts.entrySet()) {
                String offender = entry.getKey();
                int frequency = entry.getValue();
                String text = offender + " : " + frequency;
                if (frequency > 3) {
                    MostReportsOffendersTextArea.appendText(text + "\n");

                }
            }

            // Count the frequency of offenders in allRUOffendersList
            Map<String, Integer> ruOffenderCounts = new HashMap<>();
            for (String offender : allRUOffendersList) {
                ruOffenderCounts.put(offender, ruOffenderCounts.getOrDefault(offender, 0) + 1);
            }

            for (Map.Entry<String, Integer> entry : ruOffenderCounts.entrySet()) {
                String offender = entry.getKey();
                int frequency = entry.getValue();
                String text = offender + ": " + frequency + " -> RU";
                if (frequency > 3) {
                    MostReportsOffendersTextArea.appendText(text + "\n");
                }
            }
            // Concatenate the moderator stats, awaiting reports, and repeat offenders and paste into clipboard
            String myString = AllModeratorStatsTextField.getText() + "\n\n"
                    + AwaitingReportsTotalTextAreaString + "\n\n"
                    + "Repeat offenders:\n\n"
                    + MostReportsOffendersTextArea.getText();

            StringSelection stringSelection = new StringSelection(myString);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);

        }catch (Exception e) {
            AllModeratorStatsTextField.setText("Refresh reports first");
            }
    }
}
