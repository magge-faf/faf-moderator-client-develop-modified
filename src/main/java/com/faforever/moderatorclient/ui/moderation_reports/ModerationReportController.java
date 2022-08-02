package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.commons.api.dto.ModerationReportStatus;
import com.faforever.commons.replay.ReplayDataParser;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.ModerationReportService;
import com.faforever.moderatorclient.ui.BanInfoController;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.PlatformService;
import com.faforever.moderatorclient.ui.UiService;
import com.faforever.moderatorclient.ui.ViewHelper;
import com.faforever.moderatorclient.ui.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import com.google.common.base.Strings;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModerationReportController implements Controller<Region> {
    private final ObjectMapper objectMapper;
    private final ModerationReportService moderationReportService;
    private final UiService uiService;
    private final FafApiCommunicationService communicationService;
    private final PlatformService platformService;
    private final ObservableList<PlayerFX> reportedPlayersOfCurrentlySelectedReport = FXCollections.observableArrayList();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    public CheckBox hideReportsRU;
    public CheckBox FilterLogCheckBox;
    public CheckBox AutomaticallyLoadChatLogCheckBox;
    public Button CreateReportButton;
    public Button CopyReportTemplate;
    public CheckBox hideAlreadyTakenReportsCheckbox;

    @Value("${faforever.vault.replay-download-url-format}")
    private String replayDownLoadFormat;

    public Region root;
    public ChoiceBox<ChooseableStatus> statusChoiceBox;
    public TextField playerNameFilterTextField;
    public TableView<ModerationReportFX> reportTableView;
    public Button editReportButton;
    public TableView<PlayerFX> reportedPlayerView;
    public TextArea chatLogTextArea;
    public TextArea AwaitingReportsTotalTextArea;
    public Button CopyReportedUserID;
    public Button CopyChatLog;
    public Button CopyReportID;
    public Button CopyGameID;
    public Button StartReplay;


    private FilteredList<ModerationReportFX> filteredItemList;
    private ObservableList<ModerationReportFX> itemList;
    private ModerationReportFX currentlySelectedItemNotNull;

    @Override
    public SplitPane getRoot() {
        return (SplitPane) root;
    }

    public void CopyReportedUserID() {
        setSysClipboardText(CopyReportedUserID.getId());
    }

    public void CopyChatLog() {
        setSysClipboardText(CopyChatLog.getId());
    }

    public void CopyReportID() {
        setSysClipboardText(CopyReportID.getId() + ",");
    }

    public void CopyGameID() {
        setSysClipboardText(CopyGameID.getId());
    }

    public void CreateReportButton() throws IOException {
        String reported_user_id = CreateReportButton.getId();
        String url = ("https://forum.faforever.com/search?term=" + reported_user_id + "&in=titles");
        String cmd = "cmd /c " + "start chrome " + url;
        Runtime run = Runtime.getRuntime();
        run.exec(cmd);
    }

    public void StartReplay() throws IOException, InterruptedException {

        String replayUrl = "https://replay.faforever.com/"+StartReplay.getId();
        Path tempFilePath = Files.createTempFile("faf_replay_", ".fafreplay");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(replayUrl))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFilePath));

        Runtime run = Runtime.getRuntime();
        log.debug("tempFilePath: " + tempFilePath);
        String cmd = "cmd /c " + tempFilePath;
        run.exec(cmd);
        //Files.delete(tempFilePath);  // We overwriting the old file
    }

    public void CopyReportTemplate() throws FileNotFoundException {
        String report_id = CopyReportID.getId();
        String[] reported_name_array = CopyReportedUserID.getId().split(" ", 2);

        String content = new Scanner(new File("TemplateReport.txt")).useDelimiter("\\Z").next();

        if (content.contains("%s")) {
            String report_template = String.format(content, report_id, reported_name_array[0]);
            setSysClipboardText(report_template);
        }
        else {
            setSysClipboardText(content);
        }

        String report_template = String.format("""
        %s,

        - Generic insults
        - Reclaiming/Killing friendly units
        - Off mapping
        - CTRL+K all units, leaving game on own terms
        - CTRL+K ACU, leaving game on own terms
        
        !offlinemessage %s Warned for insults, reclaiming/killing friendly units and ruining the game experience for other players.
        Account suspended for 2 days. Reason: Insults, reclaiming/killing friendly units and ruining the game experience for other players.
        """,report_id, reported_name_array[0]);
    }

    public static class GlobalConstants
            //TODO
    {
        public static String numpad_one_reported_user_id = "";
        public static String numpad_two_chat_log = "";
        public static String numpad_three_game_id = "";

        public static  ArrayList<String> allReports = new ArrayList<>();
    }

    @FXML
    public void initialize() {
        counter_awaiting_total_reports = 0;
        counter_awaiting_total_ru_reports = 0;
        counter_already_taken_from_mod = 0;

        statusChoiceBox.setItems(FXCollections.observableArrayList(ChooseableStatus.values()));
        statusChoiceBox.getSelectionModel().select(ChooseableStatus.AWAITING); // default selected filter
        editReportButton.disableProperty().bind(reportTableView.getSelectionModel().selectedItemProperty().isNull());

        itemList = FXCollections.observableArrayList();
        filteredItemList = new FilteredList<>(itemList);
        renewFilter();
        SortedList<ModerationReportFX> sortedItemList = new SortedList<>(filteredItemList);
        sortedItemList.comparatorProperty().bind(reportTableView.comparatorProperty());
        ViewHelper.buildModerationReportTableView(reportTableView, sortedItemList, this::showChatLog);
        statusChoiceBox.getSelectionModel().selectedItemProperty().addListener(observable -> renewFilter());
        playerNameFilterTextField.textProperty().addListener(observable -> renewFilter());
        reportTableView.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        currentlySelectedItemNotNull = newValue;
                        reportedPlayersOfCurrentlySelectedReport.setAll(newValue.getReportedUsers());
                        if (newValue.getGame() == null) {
                            chatLogTextArea.setText("not available");
                        } else {
                            try {
                                for (PlayerFX item : newValue.getReportedUsers()) {
                                    CreateReportButton.setId(StringUtils.substringBetween(item.getRepresentation()," [id ", "]"));
                                    CreateReportButton.setText("Create report for " + StringUtils.substringBetween(item.getRepresentation()," [id ", "]"));

                                    CopyReportedUserID.setId(item.getRepresentation());
                                    CopyReportedUserID.setText(item.getRepresentation());
                                }
                            }
                            catch (Exception e) {
                                log.debug(String.valueOf(e));
                            }

                            CopyReportID.setId(newValue.getId());

                            CopyGameID.setText("Game ID: " + newValue.getGame().getId());
                            CopyGameID.setId(newValue.getGame().getId());

                            StartReplay.setId(CopyGameID.getId());
                            StartReplay.setText("Start Replay: "+ CopyGameID.getId());

                            if (AutomaticallyLoadChatLogCheckBox.isSelected()){
                                showChatLog(newValue);
                                log.debug("Game Log automatically loaded");
                                GlobalConstants.numpad_one_reported_user_id = newValue.getReportedUsers().toString();
                                GlobalConstants.numpad_three_game_id = newValue.getGame().getId();
                            } else {
                                chatLogTextArea.setText("not loaded yet");
                            }
                        }
                    }
                });

        chatLogTextArea.setText("select a report first");

        ViewHelper.buildUserTableView(platformService, reportedPlayerView, reportedPlayersOfCurrentlySelectedReport, this::addBan,
                playerFX -> ViewHelper.loadForceRenameDialog(uiService, playerFX), communicationService);

    }

    public static void setSysClipboardText(String writeMe) {
        System.setProperty("java.awt.headless", "false");
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable tText = new StringSelection(writeMe);
        clip.setContents(tText, null);
    }

    private void addBan(PlayerFX accountFX) {
        BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
        BanInfoFX ban = new BanInfoFX();
        ban.setPlayer(accountFX);
        banInfoController.setBanInfo(ban);
        banInfoController.addPostedListener(banInfoFX -> onRefreshAllReports());
        Stage banInfoDialog = new Stage();
        banInfoDialog.setTitle("Apply new ban");
        banInfoDialog.setScene(new Scene(banInfoController.getRoot()));
        banInfoController.preSetReportId(currentlySelectedItemNotNull.getId());
        banInfoDialog.showAndWait();
    }

    int counter_awaiting_total_reports = 0;
    int counter_awaiting_total_ru_reports = 0;
    int counter_already_taken_from_mod = 0;

    private void renewFilter() {
        counter_awaiting_total_reports = 0;
        counter_awaiting_total_ru_reports = 0;
        counter_already_taken_from_mod = 0;

        filteredItemList.setPredicate(moderationReportFx -> {
            String playerFilter = playerNameFilterTextField.getText().toLowerCase();
            if (!Strings.isNullOrEmpty(playerFilter)) {
                boolean reportedPlayerPositive = moderationReportFx.getReportedUsers().stream().anyMatch(accountFX -> accountFX.getLogin().toLowerCase().contains(playerFilter));
                boolean reporterPositive = moderationReportFx.getReporter().getLogin().toLowerCase().contains(playerFilter);
                if (!(reportedPlayerPositive || reporterPositive)) {
                    return false;
                }
            }
            ChooseableStatus selectedItem = statusChoiceBox.getSelectionModel().getSelectedItem();

            if (selectedItem != null) {
                ModerationReportStatus moderationReportStatus = selectedItem.getModerationReportStatus();

                try{
                    String current_line = moderationReportFx.getId() + ":" + moderationReportFx.getLastModerator().getRepresentation() + ":" + moderationReportFx.getReportStatus();
                    if(!GlobalConstants.allReports.contains(current_line) && !moderationReportFx.getReportStatus().toString().equals("AWAITING")){
                        GlobalConstants.allReports.add(current_line);
                    }
                }
                catch (Exception ignored){} // com.faforever.moderatorclient.ui.domain.ModerationReportFX.getLastModerator()" is null

                if (moderationReportFx.getReportStatus().toString().equals("AWAITING")) {
                    counter_awaiting_total_reports += 1;

                    if (moderationReportFx.getModeratorPrivateNote() != null || moderationReportFx.getLastModerator() != null
                    ){
                        counter_awaiting_total_ru_reports +=1;
                    }else {
                        for(int i = 0; i < moderationReportFx.getReportDescription().length(); i++) {
                            if(Character.UnicodeBlock.of(moderationReportFx.getReportDescription().charAt(i)).equals(Character.UnicodeBlock.CYRILLIC)) {
                                // contains Cyrillic
                                counter_awaiting_total_ru_reports +=1;
                                break;
                            }
                        }
                    }

                    if (hideAlreadyTakenReportsCheckbox.isSelected()){
                        if (moderationReportFx.getLastModerator() != null) {return false;}
                    }
                    if (hideReportsRU.isSelected()){
                        for(int i = 0; i < moderationReportFx.getReportDescription().length(); i++) {
                            if(Character.UnicodeBlock.of(moderationReportFx.getReportDescription().charAt(i)).equals(Character.UnicodeBlock.CYRILLIC)) {
                                // contains Cyrillic
                                return false;
                            }
                        }
                    }

                }


                AwaitingReportsTotalTextArea.setText(
                        "Total awaiting: " + (counter_awaiting_total_reports) +
                                "\nTotal RU awaiting: " + (counter_awaiting_total_ru_reports) +
                                "\nTotal non RU awaiting: " + (counter_awaiting_total_reports - counter_awaiting_total_ru_reports));

                return moderationReportFx.getReportStatus() == moderationReportStatus;
            }
            return true;
        });
    }

    public void onRefreshAllReports() {
        counter_awaiting_total_reports = 0;
        counter_awaiting_total_ru_reports = 0;
        counter_already_taken_from_mod = 0;
        moderationReportService.getAllReports().thenAccept(reportFxes -> Platform.runLater(() -> itemList.setAll(reportFxes))).exceptionally(throwable -> {
            log.error("error loading reports", throwable);

            return null;
        });
    }

    public void onEdit() {
        counter_awaiting_total_reports = 0;
        counter_awaiting_total_ru_reports = 0;
        counter_already_taken_from_mod = 0;
        EditModerationReportController editModerationReportController = uiService.loadFxml("ui/edit_moderation_report.fxml");
        editModerationReportController.setModerationReportFx(reportTableView.getSelectionModel().getSelectedItem());
        editModerationReportController.setOnSaveRunnable(() -> Platform.runLater(this::onRefreshAllReports));
        //statusChoiceBox.setItems(FXCollections.observableArrayList(ChooseableStatus.values()));
        Stage newCategoryDialog = new Stage();
        newCategoryDialog.setTitle("Edit Report");
        newCategoryDialog.setScene(new Scene(editModerationReportController.getRoot()));
        newCategoryDialog.showAndWait();
    }

    private enum ChooseableStatus {
        ALL(null),
        AWAITING(ModerationReportStatus.AWAITING),
        PROCESSING(ModerationReportStatus.PROCESSING),
        COMPLETED(ModerationReportStatus.COMPLETED),
        DISCARDED(ModerationReportStatus.DISCARDED);

        @Getter
        private final ModerationReportStatus moderationReportStatus;

        ChooseableStatus(ModerationReportStatus moderationReportStatus) {
            this.moderationReportStatus = moderationReportStatus;
        }
    }

    @SneakyThrows
    private void showChatLog(ModerationReportFX report) {
        GameFX game = report.getGame();
        String header = format("CHAT LOG -- Report ID {0} -- Replay ID {1} -- Game \"{2}\"\n\n",
                report.getId(), game.getId(), game.getName());
        Path tempFilePath = Files.createTempFile(format("faf_replay_", game.getId()), "");
        try {
            String replayUrl = game.getReplayUrl(replayDownLoadFormat);
            log.info("Downloading replay from {} to {}", replayUrl, tempFilePath);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(replayUrl))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFilePath));
            log.debug("Parsing replay");
            ReplayDataParser replayDataParser = new ReplayDataParser(tempFilePath, objectMapper);
            String chatLog = header + replayDataParser.getChatMessages().stream()
                    .map(message -> format("[{0}] from {1} to {2}: {3}",
                            DurationFormatUtils.formatDuration(message.getTime().toMillis(), "HH:mm:ss"),
                            message.getSender(), message.getReceiver(), message.getMessage()))
                    .collect(Collectors.joining("\n"));

            BufferedReader bufReader = new BufferedReader(new StringReader(chatLog));
            String line;
            StringBuilder chat_log_cleaned = new StringBuilder();
            String compile_sentences = "Can you give me some mass, |Can you give me some energy, |" +
                    "Can you give me one Engineer, | to notify: | to allies: Sent Mass | to allies: Sent Energy ";
            while( (line=bufReader.readLine()) != null )
            {
                Pattern pattern = Pattern.compile(compile_sentences);
                Matcher matcher = pattern.matcher(line);
                boolean matchFound = matcher.find();
                if (matchFound & FilterLogCheckBox.isSelected()) {
                    // throw away current chat line
                }else {
                    chat_log_cleaned.append(line).append("\n");
                }
            }
            CopyChatLog.setId(chat_log_cleaned.toString());
            CopyChatLog.setText("Copy Chat Log");
            GlobalConstants.numpad_two_chat_log = chat_log_cleaned.toString();
            chatLogTextArea.setText(chat_log_cleaned.toString());

        } catch (Exception e) {
            log.error("Loading replay {} failed", game, e);
            StartReplay.setText("Replay not available");
            CopyChatLog.setText("Chat Log not available");
            chatLogTextArea.setText(header + format("Loading replay failed due to {0}: \n{1}", e, e.getMessage()));
        }
        Files.delete(tempFilePath);
    }
}