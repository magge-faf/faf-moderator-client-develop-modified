package com.faforever.moderatorclient.ui.main_window;

import com.faforever.moderatorclient.ui.moderation_reports.ModerationReportController;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import com.faforever.moderatorclient.ui.*;
import javafx.fxml.FXML;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
@Slf4j
@RequiredArgsConstructor
public class SettingsController implements Controller<Region> {

    public VBox root;
    public TextField AccountNameOrEmailTextField;
    public TextField AccountPasswordTextField;
    public Button SaveAccountButton;
    public TextField PathAccountFile;
    public Button BanDataPermanentVSN;
    public Button BanDataTemporaryVSN;
    public Button BanDataPermanentIP;
    public TextField AllModeratorStatsTextField;
    public Button TemplateCompletedButton;
    public Button TemplateDiscardedButton;
    public Button TemplateReportButton;
    public TextField MostReportsOffendersTextField;
    public Button LoadAllReportsAndModeratorStatsAndTopOffendersButton;

    @Override
    public VBox getRoot() {return root;}

    @FXML
    public void initialize() {
        File f = new File("account_credentials.txt");
        if(f.exists() && !f.isDirectory()) {
            Path pathCredentialsFile = Path.of("account_credentials.txt");
            PathAccountFile.setText(String.valueOf(pathCredentialsFile.toAbsolutePath()));
            List<String> result;
            try (Stream<String> lines = Files.lines(pathCredentialsFile)) {
                result = lines.collect(Collectors.toList());
                String nameOrEmail = result.get(0);
                String password = result.get(1);
                AccountNameOrEmailTextField.setText(nameOrEmail);
                AccountPasswordTextField.setText(password);
            }catch (Exception e){
                log.debug(String.valueOf(e));
            }
        }
    }

    public void SaveAccountButton() {
        try {
            FileWriter fw = new FileWriter("account_credentials.txt",false);
            fw.write(AccountNameOrEmailTextField.getText() + "\n" + AccountPasswordTextField.getText());
            fw.flush();
            fw.close();
            SaveAccountButton.setText("Credentials were saved.");
        }
        catch(Exception e) {
            log.error(String.valueOf(e));
        }
    }

    public void BanDataPermanentVSN() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("Notepad.exe", "BlacklistedPermanentVSN.txt");
        pb.start();
    }

    public void BanDataTemporaryVSN() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("Notepad.exe", "BlacklistedTemporaryVSN.txt");
        pb.start();
    }

    public void BanDataPermanentIP() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("Notepad.exe", "BlacklistedPermanentIP.txt");
        pb.start();
    }

    public void LoadAllModeratorStatsButton() {
        try {
            String allReportsString = String.valueOf(ModerationReportController.GlobalConstants.allReports);

            allReportsString = allReportsString.replaceAll("\\[.*?]","");
            allReportsString = allReportsString.replaceAll("maudlin27","maudlin");
            allReportsString = allReportsString.replaceAll("angelofd347h","angelofd");

            String[] dataList = allReportsString.split(",");
            List<String> test = new ArrayList<>(Collections.singletonList(""));

            for (String item : dataList) {
                item = Arrays.toString(item.split(":"));
                test.add(Arrays.toString(new String[]{item.split(",")[1]}));
            }

            Set<String> unique = new HashSet<>(test);
            List<String> totalProcessedReportsRaw = new ArrayList<>();
            List<String> totalProcessedReportsProcessed = new ArrayList<>();

            for (String key : unique) {
                if (!key.equals("")){
                    totalProcessedReportsRaw.add(key + ": " + Collections.frequency(test, key));
                }
            }

            for (String item : totalProcessedReportsRaw) {
                item = item.replaceAll("\\[", "").replaceAll("]", "").replaceAll(" ", "");
                totalProcessedReportsProcessed.add(item);
            }

            totalProcessedReportsProcessed.sort(new Comparator<String>() {
                public int compare(String o1, String o2) {
                    return extractInt(o1) - extractInt(o2);
                }

                int extractInt(String s) {
                    String num = s.replaceAll("\\D", "");
                    // return 0 if no digits found
                    return num.isEmpty() ? 0 : Integer.parseInt(num);
                }
            });

            List<String> finalList = new ArrayList<>();

            // give 'em their numbers back
            for (String item : totalProcessedReportsProcessed){
                if (item.contains("angelofd")||item.contains("maudlin")){
                    item = item.replaceAll("angelofd","angelofd347h");
                    item = item.replaceAll("maudlin","maudlin27");
                }
                if (!item.contains("DISCARDED")){  // one bugged report which has discarded as moderator name ...
                    finalList.add(item);
                }
            }

            List<?> shallowCopy = finalList.subList(0, finalList.size());
            Collections.reverse(shallowCopy);

            AllModeratorStatsTextField.setText(String.valueOf(finalList));
            GlobalConstants.AllReportsStats = String.valueOf(finalList);

        }catch (Exception e) {
            AllModeratorStatsTextField.setText("Refresh reports first");
            }
    }

    public void TemplateCompletedButton() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("Notepad.exe", "TemplateCompleted.txt");
        pb.start();
    }

    public void TemplateDiscardedButton() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("Notepad.exe", "TemplateDiscarded.txt");
        pb.start();
    }

    public void TemplateReportButton() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("Notepad.exe", "TemplateReport.txt");
        pb.start();
    }

    public void LoadAllReportsAndModeratorStatsAndTopOffendersButton() {
        LoadAllModeratorStatsButton();

        String allOffendersString = String.valueOf(ModerationReportController.GlobalConstants.allOffenders);
        String allOffendersStringProcessed = allOffendersString.replace("[","").replace("]","");
        List<String> allOffendersList = new ArrayList<>(Arrays.asList(allOffendersStringProcessed.split(",")));
        
        Set<String> mySet = new HashSet<>(allOffendersList);

        String TotalAmountReportsForOffender;
        StringBuilder OffenderNameAndID = new StringBuilder();

        for(String s: mySet){
            TotalAmountReportsForOffender = s + " " + Collections.frequency(allOffendersList,s);

            if (TotalAmountReportsForOffender.endsWith("1") || TotalAmountReportsForOffender.endsWith("2")){
                //ignore offenders with only 1 or 2 reports
                //will be a problem for future me when result is 11 or 12, 21, 22, etc
                continue;
            }
            else {
                OffenderNameAndID.append(TotalAmountReportsForOffender).append(" | ");}
        }
        MostReportsOffendersTextField.setText(String.valueOf(OffenderNameAndID));
    }

    public static class GlobalConstants
        //carried the status of all reports from the ReportsTab to SettingsController for further processing
        {
            public static String AllReportsStats = "";
        }

}
