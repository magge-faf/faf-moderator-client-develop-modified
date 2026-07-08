package com.faforever.moderatorclient.ui;

import com.faforever.commons.api.dto.BanDurationType;
import com.faforever.commons.api.dto.BanInfo;
import com.faforever.commons.api.dto.BanLevel;
import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.commons.api.dto.BanStatus;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.LobbyModerationService;
import com.faforever.moderatorclient.api.domain.BanService;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.ModerationReportFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import com.faforever.moderatorclient.mapstruct.PlayerMapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.prefs.Preferences;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
@RequiredArgsConstructor
public class BanInfoController implements Controller<Pane> {
    private static final Preferences BAN_DIALOG_PREFERENCES = Preferences.userNodeForPackage(BanInfoController.class);
    private static final String KICK_FROM_GAME_PREF = "banDialog.kickFromGame";
    private static final String KICK_FROM_LOBBY_PREF = "banDialog.kickFromLobby";

    private final FafApiCommunicationService fafApi;
    private final BanService banService;
    private final LobbyModerationService lobbyModerationService;
    private final PlayerMapper playerMapper;
    public GridPane root;
    public TextField affectedUserTextField;
    public TextField banAuthorTextField;
    public TextField banReasonTextField;
    public TextField revocationReasonTextField;
    public TextField revocationAuthorTextField;
    public TextField banDaysTextField;
    public TextField untilTextField;
    public Label untilDateTimeValidateLabel;
    public RadioButton permanentBanRadioButton;
    public RadioButton multiAccountBanRadioButton;
    public RadioButton forNoOfDaysBanRadioButton;
    public RadioButton temporaryBanRadioButton;
    public RadioButton warningBanRadioButton;
    public RadioButton chatOnlyBanRadioButton;
    public RadioButton vaultBanRadioButton;
    public RadioButton globalBanRadioButton;
    public Button revokeButton;
    public Button specificTimeButton;
    public Button saveButton;
    public Button cancelButton;
    public Label userLabel;
    public Label banIsRevokedNotice;
    public Label editModeNoticeLabel;
    public Label postBanActionsLabel;
    public TextField revocationTimeTextField;
    public VBox revokeOptions;
    public VBox specificTimeSection;
    public VBox postBanActionsBox;
    public CheckBox kickFromGameCheckBox;
    public CheckBox kickFromLobbyCheckBox;
    public TextField reportIdTextField;
    public ToggleGroup banDuration;

    private static final String MULTI_ACCOUNT_REASON =
            "Account suspended due to detection of multiple accounts for the same user.";

    @Getter
    private BanInfoFX banInfo;
    @Getter
    private String dialogTitle = "Apply new ban";
    private BanStatus originalBanStatus;
    private Consumer<BanInfoFX> postedListener;
    private Runnable onBanRevoked;

    public void addRevokedListener(Runnable listener) {
        this.onBanRevoked = listener;
    }

    public void addPostedListener(Consumer<BanInfoFX> listener) {
        this.postedListener = listener;
    }

    @Override
    public Pane getRoot() {
        return root;
    }

    @FXML
    public void initialize() {
        banIsRevokedNotice.managedProperty().bind(banIsRevokedNotice.visibleProperty());
        editModeNoticeLabel.managedProperty().bind(editModeNoticeLabel.visibleProperty());
        postBanActionsBox.managedProperty().bind(postBanActionsBox.visibleProperty());
        banReasonTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.regionMatches(true, 0, "warning", 0, 7)) {
                warningBanRadioButton.setSelected(true);
            }

            Pattern pattern = Pattern.compile("(?i)(\\d+)\\s+day\\s+ban");
            Matcher matcher = pattern.matcher(newValue);
            if (matcher.find()) {
                String numDays = matcher.group(1);
                log.debug("Detected number before 'day ban': {}", numDays);
                banDaysTextField.setText(numDays);
            } else {
                log.debug("No number before 'day ban' found");
            }
        });

        multiAccountBanRadioButton.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                banReasonTextField.setText(MULTI_ACCOUNT_REASON);
            }
        });

        initializePostBanActions();
    }

    public void onSpecificTimeToggle() {
        boolean show = !specificTimeSection.isVisible();
        specificTimeSection.setVisible(show);
        specificTimeSection.setManaged(show);
        specificTimeButton.setText(show ? "▾ Specific date/time..." : "▸ Specific date/time...");
        if (!show && temporaryBanRadioButton.isSelected()) {
            forNoOfDaysBanRadioButton.setSelected(true);
        }
    }

    public void onRevokeTimeTextChanged() {
        revocationTimeTextField.setStyle("-fx-text-fill: green");
        try {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(revocationTimeTextField.getText());
        } catch (Exception e) {
            revocationTimeTextField.setStyle("-fx-text-fill: red");
        }
    }

    public void setBanInfo(BanInfoFX banInfo) {
        this.banInfo = banInfo;
        this.originalBanStatus = banInfo.getId() == null ? null : banInfo.getBanStatus();
        refreshDialogMode();

        if (banInfo.getId() != null) {
            revokeOptions.setDisable(false);

            affectedUserTextField.setText(banInfo.getPlayer().representationProperty().get());
            Optional.ofNullable(banInfo.getAuthor()).ifPresent(author -> banAuthorTextField.setText(author.representationProperty().get()));

            banReasonTextField.setText(banInfo.getReason());

            revocationReasonTextField.setDisable(false);
            revokeButton.setDisable(false);

            permanentBanRadioButton.setSelected(banInfo.getDuration() == BanDurationType.PERMANENT);
            if (banInfo.getDuration() == BanDurationType.TEMPORARY) {
                specificTimeSection.setVisible(true);
                specificTimeSection.setManaged(true);
                specificTimeButton.setText("▾ Specific date/time...");
                temporaryBanRadioButton.setSelected(true);
            }
            Optional.ofNullable(banInfo.getExpiresAt()).ifPresent(offsetDateTime -> untilTextField.setText(offsetDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

            if (banInfo.getRevokeTime() != null) {
                banIsRevokedNotice.setVisible(true);
                revocationReasonTextField.setText(banInfo.getRevokeReason());
                revocationTimeTextField.setText(banInfo.getRevokeTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                log.debug(banInfo.getRevokeTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                revocationAuthorTextField.setText(banInfo.getRevokeAuthor() == null ? "" : banInfo.getRevokeAuthor().getLogin());
            } else {
                revocationTimeTextField.setText(OffsetDateTime.now().atZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            // chat ban is not supported
            //chatOnlyBanRadioButton.setSelected(banInfo.getLevel() == BanLevel.CHAT);
            vaultBanRadioButton.setSelected(banInfo.getLevel() == BanLevel.VAULT);
            globalBanRadioButton.setSelected(banInfo.getLevel() == BanLevel.GLOBAL);
            ModerationReportFX moderationReportFx = banInfo.getModerationReport();
            if (moderationReportFx != null) {
                reportIdTextField.setText(moderationReportFx.getId());
            }
        } else {
            PlayerFX player = banInfo.getPlayer();
            if (player != null) {
                affectedUserTextField.setText(player.representationProperty().get());
            } else {
                affectedUserTextField.setEditable(true);
                affectedUserTextField.setDisable(false);
                userLabel.setText("Affected User ID");
            }
        }

        refreshDialogMode();
    }

    public void onSave() {
        Assert.notNull(banInfo, "You can't save if banInfo is null.");

        if (!validate()) {
            return;
        }
        if (banInfo.getPlayer() == null) {
            PlayerFX playerFX = new PlayerFX();
            playerFX.setId(affectedUserTextField.getText());
            banInfo.setPlayer(playerFX);
        }

        banInfo.setReason(banReasonTextField.getText());

        if (forNoOfDaysBanRadioButton.isSelected())
            banInfo.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(Long.parseLong(banDaysTextField.getText())));
        else if (temporaryBanRadioButton.isSelected()) {
            banInfo.setExpiresAt(OffsetDateTime.of(LocalDateTime.parse(untilTextField.getText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME), ZoneOffset.UTC));
        } else if (warningBanRadioButton.isSelected()) {
            banInfo.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        } else {
            // permanentBanRadioButton or multiAccountBanRadioButton — no expiry
            banInfo.setExpiresAt(null);
        }

        if (chatOnlyBanRadioButton.isSelected()) {
            banInfo.setLevel(BanLevel.CHAT);
        } else if (vaultBanRadioButton.isSelected()) {
            banInfo.setLevel(BanLevel.VAULT);
        } else {
            banInfo.setLevel(BanLevel.GLOBAL);
        }

        if (!StringUtils.isBlank(reportIdTextField.getText())) {
            ModerationReportFX moderationReportFx = new ModerationReportFX();
            moderationReportFx.setId(reportIdTextField.getText());
            banInfo.setModerationReport(moderationReportFx);
        }


        if (banInfo.getId() == null) {
            log.debug("Creating ban for player '{}' with reason: {}", banInfo.getPlayer().toString(), banReasonTextField.getText());
            String newBanId = banService.createBan(banInfo);
            BanInfoFX loadedBanInfo = banService.getBanInfoById(newBanId);
            if (postedListener != null) {
                postedListener.accept(loadedBanInfo);
            }
            runPostBanActions();
        } else {
            log.debug("Updating ban id '{}'", banInfo.getId());
            if (originalBanStatus == BanStatus.BANNED) {
                if (!ViewHelper.confirmDialog(
                        "Disable or Replace active ban",
                        "Saving here will disable the current active ban and create a new replacement ban with the updated values. Use Revocation instead if you only want to disable the ban."
                )) {
                    return;
                }
                // Active ban: revoke old entry and create a new one so all fields are persisted
                String newBanId = banService.revokeThenCreateBan(banInfo);
                BanInfoFX loadedBanInfo = banService.getBanInfoById(newBanId);
                if (postedListener != null) {
                    postedListener.accept(loadedBanInfo);
                }
                runPostBanActions();
            } else {
                banService.patchBanInfo(banInfo);
                runPostBanActions();
            }
        }
        close();
    }

    private boolean validate() {
        List<String> validationErrors = new ArrayList<>();

        if (banInfo.getPlayer() == null) {
            try {
                Integer.parseInt(affectedUserTextField.getText());
            } catch (Exception e) {
                validationErrors.add("You must specify an affected user");
            }
        }

        if (StringUtils.isBlank(banReasonTextField.getText())) {
            validationErrors.add("No ban reason is given.");
        }

        if (!forNoOfDaysBanRadioButton.isSelected() && !temporaryBanRadioButton.isSelected()
                && !permanentBanRadioButton.isSelected() && !warningBanRadioButton.isSelected()
                && !multiAccountBanRadioButton.isSelected()) {
            validationErrors.add("No ban duration is selected.");
        }

        if (!chatOnlyBanRadioButton.isSelected() &&
                !vaultBanRadioButton.isSelected() &&
                !globalBanRadioButton.isSelected()) {
            validationErrors.add("No ban type is selected.");
        }

        if (!StringUtils.isBlank(reportIdTextField.getText())) {
            try {
                Integer.parseInt(reportIdTextField.getText());
            } catch (Exception e) {
                validationErrors.add("Report ID must be a number.");
            }
        }

        if (forNoOfDaysBanRadioButton.isSelected()) {
            try {
                Long.parseUnsignedLong(banDaysTextField.getText());
            } catch (NumberFormatException e) {
                validationErrors.add("Invalid number of days.");
            }
        }

        if (temporaryBanRadioButton.isSelected()) {
            try {
                LocalDateTime.parse(untilTextField.getText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e) {
                validationErrors.add("Expiration date of ban is invalid.");
            }
        }

        if (!validationErrors.isEmpty()) {
            ViewHelper.errorDialog("Validation failed",
                    String.join("\n", validationErrors)
            );
            return false;
        }
        return true;
    }

    public void onRevoke() {
        Assert.notNull(banInfo, "You can't revoke if banInfo is null.");
        List<String> errors = new ArrayList<>();

        String revocationReason = revocationReasonTextField.getText();

        if (StringUtils.isBlank(revocationReason)) {
            errors.add("The reason of revocation must not be empty.");
        }
        OffsetDateTime revokeTime = null;
        try {
            revokeTime = OffsetDateTime.of(LocalDateTime.parse(revocationTimeTextField.getText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME), ZoneOffset.UTC);
        } catch (Exception e) {
            log.debug("Revoke time invalid", e);
            errors.add("Invalid date for revocation.");
        }

        if (!errors.isEmpty()) {
            ViewHelper.errorDialog("Could not revoke",
                    String.join("\n", errors));
            return;
        }

        log.debug("Revoking ban id '{}' with reason: {}", banInfo.getId(), revocationReason);

        PlayerFX author = new PlayerFX();
        author.setId(fafApi.getMeResult().getId());
        banInfo.setRevokeAuthor(author);
        banInfo.setRevokeReason(revocationReason);
        banInfo.setRevokeTime(revokeTime);
        banInfo.setUpdateTime(OffsetDateTime.now());

        BanInfo banInfoUpdate = new BanInfo();
        banInfoUpdate.setId(banInfo.getId());
        banInfoUpdate.setPlayer(playerMapper.map(banInfo.getPlayer()));
        banInfoUpdate.setRevokeReason(revocationReason);
        banInfoUpdate.setRevokeTime(revokeTime);

        banService.updateBan(banInfoUpdate);
        if (onBanRevoked != null) {
            onBanRevoked.run();
        }
        close();
    }

    public void onAbort() {
        close();
    }

    public void close() {
        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }

    public void onDurationTextChange() {
        if (untilTextField.getText().isEmpty()) {
            untilDateTimeValidateLabel.setText("");
            return;
        }
        try {
            untilDateTimeValidateLabel.setText("valid");
            untilDateTimeValidateLabel.setStyle("-fx-text-fill: green");
        } catch (DateTimeParseException e) {
            untilDateTimeValidateLabel.setText("invalid");
            untilDateTimeValidateLabel.setStyle("-fx-text-fill: red");
        }
    }

    public void preSetReportId(String id) {
        reportIdTextField.setText(id);
        reportIdTextField.setDisable(true);
    }

    private void initializePostBanActions() {
        boolean canKickPlayers = fafApi.hasPermission(GroupPermission.ADMIN_KICK_SERVER);
        postBanActionsBox.setVisible(canKickPlayers);
        if (!canKickPlayers) {
            return;
        }

        kickFromGameCheckBox.setSelected(BAN_DIALOG_PREFERENCES.getBoolean(KICK_FROM_GAME_PREF, false));
        kickFromLobbyCheckBox.setSelected(BAN_DIALOG_PREFERENCES.getBoolean(KICK_FROM_LOBBY_PREF, false));

        kickFromGameCheckBox.selectedProperty().addListener((observable, oldValue, newValue) ->
                BAN_DIALOG_PREFERENCES.putBoolean(KICK_FROM_GAME_PREF, newValue));
        kickFromLobbyCheckBox.selectedProperty().addListener((observable, oldValue, newValue) ->
                BAN_DIALOG_PREFERENCES.putBoolean(KICK_FROM_LOBBY_PREF, newValue));
    }

    private void runPostBanActions() {
        if (!postBanActionsBox.isVisible() || (!kickFromGameCheckBox.isSelected() && !kickFromLobbyCheckBox.isSelected())) {
            return;
        }

        int playerId;
        try {
            playerId = Integer.parseInt(banInfo.getPlayer().getId());
        } catch (Exception e) {
            ViewHelper.errorDialog("Kick failed",
                    "The ban was saved, but the player could not be kicked because the player id is missing or invalid.");
            return;
        }

        // Capture checkbox state on FX thread before entering async block
        boolean shouldKickFromGame = kickFromGameCheckBox.isSelected();
        boolean shouldKickFromLobby = kickFromLobbyCheckBox.isSelected();

        CompletableFuture.runAsync(() -> {
            if (shouldKickFromGame && shouldKickFromLobby) {
                lobbyModerationService.kickFromGameAndClient(playerId);
            } else if (shouldKickFromGame) {
                lobbyModerationService.kickFromGame(playerId);
            } else if (shouldKickFromLobby) {
                lobbyModerationService.kickFromClient(playerId);
            }
        }).exceptionally(throwable -> {
            log.error("Failed to apply post-ban kick actions for player {}", playerId, throwable);
            javafx.application.Platform.runLater(() -> ViewHelper.errorDialog(
                    "Kick failed",
                    "The ban was saved, but the kick action failed.\n" + rootCauseMessage(throwable)
            ));
            return null;
        });
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private void refreshDialogMode() {
        if (banInfo == null || banInfo.getId() == null) {
            dialogTitle = "Apply new ban";
            editModeNoticeLabel.setVisible(false);
            saveButton.setText("Apply ban");
            cancelButton.setText("Cancel");
            return;
        }

        if (originalBanStatus == BanStatus.BANNED) {
            dialogTitle = "Replace or disable active ban";
            editModeNoticeLabel.setText("Saving this form will disable the current active ban and create a new replacement ban with the updated values. The old ban stays in history as disabled. Use Revocation below only if you want to disable the ban without creating a replacement.");
            editModeNoticeLabel.setVisible(true);
            saveButton.setText("Disable old ban and create replacement");
            cancelButton.setText("Cancel replacement");
            return;
        }

        if (originalBanStatus == BanStatus.EXPIRED) {
            dialogTitle = "Edit expired ban";
            editModeNoticeLabel.setText("This ban is already expired. Saving updates this existing record only and will not create a replacement ban.");
            editModeNoticeLabel.setVisible(true);
            saveButton.setText("Save expired ban");
            cancelButton.setText("Cancel");
            return;
        }

        dialogTitle = "Edit disabled ban";
        editModeNoticeLabel.setText("This ban is already disabled. Saving updates this existing record only and will not create a replacement ban.");
        editModeNoticeLabel.setVisible(true);
        saveButton.setText("Save disabled ban");
        cancelButton.setText("Cancel");
    }
}
