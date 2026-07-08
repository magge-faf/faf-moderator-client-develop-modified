package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.LobbyModerationService;
import com.faforever.moderatorclient.api.TokenService;
import com.faforever.moderatorclient.api.domain.UserService;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.irc.IrcChannelSnapshot;
import com.faforever.moderatorclient.irc.IrcClient;
import com.faforever.moderatorclient.irc.IrcChannelNotificationType;
import com.faforever.moderatorclient.irc.IrcConfiguration;
import com.faforever.moderatorclient.irc.IrcConnectionState;
import com.faforever.moderatorclient.irc.IrcConnectionStatus;
import com.faforever.moderatorclient.irc.IrcListenerRegistration;
import com.faforever.moderatorclient.irc.IrcMessageEntry;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class IrcChatController implements Controller<BorderPane> {
    private static final DateTimeFormatter MESSAGE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final IrcClient ircClient;
    private final LocalPreferences localPreferences;
    private final FafApiCommunicationService fafApiCommunicationService;
    private final LobbyModerationService lobbyModerationService;
    private final TokenService tokenService;
    private final UserService userService;

    public BorderPane root;
    public Label serverInfoLabel;
    public Label statusIndicatorLabel;
    public Label connectionStatusLabel;
    public Label activeTopicLabel;
    public Label usersHeaderLabel;
    public TextField nicknameField;
    public CheckBox autoConnectCheckBox;
    public CheckBox debugTrafficCheckBox;
    public CheckBox suppressJoinLeaveNoiseCheckBox;
    public Button connectButton;
    public Button disconnectButton;
    public TextField joinChannelField;
    public TextField userSearchField;
    public TextField kickLookupField;
    public Button joinChannelButton;
    public Button leaveChannelButton;
    public Button pmSelectedUserButton;
    public Button kickGameButton;
    public Button kickClientButton;
    public Button kickGameAndClientButton;
    public ListView<IrcChannelSnapshot> channelListView;
    public ListView<IrcMessageEntry> messageListView;
    public ListView<String> userListView;
    public TextField messageInputField;
    public Button sendMessageButton;

    private final ObservableList<IrcChannelSnapshot> channels = FXCollections.observableArrayList();
    private final ObservableList<IrcMessageEntry> messages = FXCollections.observableArrayList();
    private final ObservableList<String> users = FXCollections.observableArrayList();

    private IrcListenerRegistration connectionRegistration;
    private IrcListenerRegistration eventRegistration;
    private boolean suppressSelectionHandler;
    private boolean moderationActionInProgress;

    @FXML
    public void initialize() {
        serverInfoLabel.setText(IrcConfiguration.DEFAULT_HOST + ":" + IrcConfiguration.DEFAULT_PORT + "  defaults #aeolus, #moderators");

        LocalPreferences.TabIrcChat tab = localPreferences.getTabIrcChat();
        nicknameField.setText(resolveModeratorNickname(tab));
        nicknameField.setEditable(false);
        autoConnectCheckBox.setSelected(tab.isAutoConnectOnStartup());
        debugTrafficCheckBox.setSelected(tab.isDebugTraffic());
        suppressJoinLeaveNoiseCheckBox.setSelected(tab.isSuppressJoinLeaveNoise());
        joinChannelField.setText(IrcConfiguration.DEFAULT_CHANNEL);

        channelListView.setItems(channels);
        messageListView.setItems(messages);
        userListView.setItems(users);

        configureChannelListView();
        configureMessageListView();
        configureModerationButtons();

        channelListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (!suppressSelectionHandler && newValue != null) {
                showChannel(newValue.name());
            }
        });

        connectionRegistration = ircClient.addConnectionListener(event ->
                Platform.runLater(() -> updateConnectionState(event.state())));
        eventRegistration = ircClient.addEventListener(event ->
                Platform.runLater(this::refreshChannelListOnly));
        suppressJoinLeaveNoiseCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            IrcChannelSnapshot selected = channelListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                renderChannel(selected.name());
            }
        });
        userSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            IrcChannelSnapshot selected = channelListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                renderChannel(selected.name());
            }
        });
        kickLookupField.textProperty().addListener((observable, oldValue, newValue) ->
                updateConnectionState(ircClient.getConnectionState()));
        userListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                updateConnectionState(ircClient.getConnectionState()));

        updateConnectionState(ircClient.getConnectionState());
        refreshChannelListOnly();

        if (tab.isAutoConnectOnStartup() && ircClient.getConnectionState().status() == IrcConnectionStatus.DISCONNECTED
                && !nicknameField.getText().isBlank()) {
            Platform.runLater(this::connect);
        }
    }

    public void connect() {
        IrcConfiguration configuration = buildConfiguration();
        ircClient.connect(configuration);
    }

    public void disconnect() {
        ircClient.disconnect();
    }

    public void joinChannel() {
        String channel = IrcConfiguration.normalizeChannel(joinChannelField.getText());
        if (channel.isBlank()) {
            return;
        }

        ircClient.joinChannel(channel);
        LocalPreferences.TabIrcChat tab = localPreferences.getTabIrcChat();
        LinkedHashSet<String> savedChannels = new LinkedHashSet<>(tab.getAutoJoinChannels());
        savedChannels.add(channel);
        tab.setAutoJoinChannels(new ArrayList<>(savedChannels));
        refreshChannelListOnly();
        selectChannel(channel);
        joinChannelField.clear();
    }

    public void leaveSelectedChannel() {
        IrcChannelSnapshot selected = channelListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        ircClient.leaveChannel(selected.name());
        LocalPreferences.TabIrcChat tab = localPreferences.getTabIrcChat();
        List<String> savedChannels = new ArrayList<>(tab.getAutoJoinChannels());
        savedChannels.removeIf(channel -> channel.equalsIgnoreCase(selected.name()));
        if (savedChannels.isEmpty()) {
            savedChannels.addAll(IrcConfiguration.DEFAULT_AUTO_JOIN_CHANNELS);
        }
        tab.setAutoJoinChannels(savedChannels);
        refreshChannelListOnly();
    }

    public void sendMessage() {
        IrcChannelSnapshot selected = channelListView.getSelectionModel().getSelectedItem();
        String message = messageInputField.getText();
        if (selected == null || message == null || message.isBlank()) {
            return;
        }

        ircClient.sendMessage(selected.name(), message);
        messageInputField.clear();
    }

    public void openPrivateMessage() {
        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser == null || selectedUser.isBlank()) {
            return;
        }

        String conversation = "@" + stripPrefix(selectedUser);
        ircClient.openPrivateConversation(conversation);
        refreshChannelListOnly();
        selectChannel(conversation);
        showChannel(conversation);
    }

    public void kickSelectedUserFromGame() {
        kickSelectedUser(KickTarget.GAME);
    }

    public void kickSelectedUserFromClient() {
        kickSelectedUser(KickTarget.CLIENT);
    }

    public void kickSelectedUserFromGameAndClient() {
        kickSelectedUser(KickTarget.GAME_AND_CLIENT);
    }

    public void onSave() {
        LocalPreferences.TabIrcChat tab = localPreferences.getTabIrcChat();
        tab.setNickname(nicknameField.getText() == null ? "" : nicknameField.getText().trim());
        tab.setAutoConnectOnStartup(autoConnectCheckBox.isSelected());
        tab.setDebugTraffic(debugTrafficCheckBox.isSelected());
        tab.setSuppressJoinLeaveNoise(suppressJoinLeaveNoiseCheckBox.isSelected());
        IrcChannelSnapshot selected = channelListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            tab.setSelectedChannel(selected.name());
        }
    }

    @Override
    public BorderPane getRoot() {
        return root;
    }

    private IrcConfiguration buildConfiguration() {
        LocalPreferences.TabIrcChat tab = localPreferences.getTabIrcChat();
        LinkedHashSet<String> autoJoinChannels = new LinkedHashSet<>();
        if (tab.getAutoJoinChannels() != null) {
            tab.getAutoJoinChannels().stream()
                    .map(IrcConfiguration::normalizeChannel)
                    .filter(channel -> !channel.isBlank())
                    .forEach(autoJoinChannels::add);
        }
        if (autoJoinChannels.isEmpty()) {
            autoJoinChannels.addAll(IrcConfiguration.DEFAULT_AUTO_JOIN_CHANNELS);
        }

        return new IrcConfiguration(
                IrcConfiguration.DEFAULT_HOST,
                IrcConfiguration.DEFAULT_PORT,
                resolveModeratorNickname(localPreferences.getTabIrcChat()),
                debugTrafficCheckBox.isSelected(),
                autoJoinChannels
        );
    }

    private void refreshChannelListOnly() {
        String selectedChannel = Optional.ofNullable(channelListView.getSelectionModel().getSelectedItem())
                .map(IrcChannelSnapshot::name)
                .orElse(localPreferences.getTabIrcChat().getSelectedChannel());

        suppressSelectionHandler = true;
        try {
            channels.setAll(ircClient.getChannelSnapshots());
            if (selectedChannel != null && !selectedChannel.isBlank()) {
                selectChannel(selectedChannel);
            } else if (!channels.isEmpty() && channelListView.getSelectionModel().getSelectedItem() == null) {
                channelListView.getSelectionModel().select(0);
            }
        } finally {
            suppressSelectionHandler = false;
        }

        IrcChannelSnapshot selected = channelListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            renderChannel(selected.name());
        } else {
            messages.clear();
            users.clear();
            usersHeaderLabel.setText("Users (0 online)");
            activeTopicLabel.setText("No channel selected");
        }
    }

    private void showChannel(String channelName) {
        localPreferences.getTabIrcChat().setSelectedChannel(channelName);
        ircClient.markChannelRead(channelName);
        renderChannel(channelName);
        refreshChannelListOnly();
    }

    private void renderChannel(String channelName) {
        ircClient.getChannelSnapshot(channelName).ifPresentOrElse(snapshot -> {
            messages.setAll(filterMessages(snapshot.history()));
            users.setAll(filterUsers(snapshot.users()));
            usersHeaderLabel.setText(String.format(Locale.ROOT, "Users (%d online)", snapshot.users().size()));
            activeTopicLabel.setText(snapshot.topic().isBlank() ? "No topic set" : snapshot.topic());
            Platform.runLater(() -> messageListView.scrollTo(Math.max(messages.size() - 1, 0)));
        }, () -> {
            messages.clear();
            users.clear();
            usersHeaderLabel.setText("Users (0 online)");
            activeTopicLabel.setText("No topic set");
        });
    }

    private void updateConnectionState(IrcConnectionState state) {
        connectionStatusLabel.setText(state.statusMessage());
        statusIndicatorLabel.setText("●");
        statusIndicatorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: " + colorForStatus(state.status()) + ";");

        boolean connected = state.status() == IrcConnectionStatus.CONNECTED;
        boolean busy = state.status() == IrcConnectionStatus.CONNECTING
                || state.status() == IrcConnectionStatus.AUTHENTICATING
                || state.status() == IrcConnectionStatus.RECONNECTING
                || state.status() == IrcConnectionStatus.DISCONNECTING;

        connectButton.setDisable(busy || connected);
        disconnectButton.setDisable(state.status() == IrcConnectionStatus.DISCONNECTED || state.status() == IrcConnectionStatus.DISCONNECTING);
        joinChannelButton.setDisable(!connected);
        leaveChannelButton.setDisable(!connected || channelListView.getSelectionModel().getSelectedItem() == null);
        sendMessageButton.setDisable(!connected || channelListView.getSelectionModel().getSelectedItem() == null);
        pmSelectedUserButton.setDisable(!connected || userListView.getSelectionModel().getSelectedItem() == null);
        boolean canKickUser = connected && !moderationActionInProgress && hasKickTarget();
        kickGameButton.setDisable(!canKickUser);
        kickClientButton.setDisable(!canKickUser);
        kickGameAndClientButton.setDisable(!canKickUser);
        messageInputField.setDisable(!connected);
    }

    private void configureModerationButtons() {
        boolean hasKickPermission = fafApiCommunicationService.hasPermission(GroupPermission.ADMIN_KICK_SERVER);
        for (Button button : List.of(kickGameButton, kickClientButton, kickGameAndClientButton)) {
            button.setVisible(hasKickPermission);
            button.setManaged(hasKickPermission);
        }
    }

    private void configureChannelListView() {
        channelListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(IrcChannelSnapshot item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                String text = item.unreadCount() > 0
                        ? String.format(Locale.ROOT, "%s (%d)", item.name(), item.unreadCount())
                        : item.name();
                setText(item.joined() ? text : text + " [left]");
                setStyle(item.unreadCount() > 0 ? "-fx-font-weight: bold;" : "");
            }
        });
    }

    private void configureMessageListView() {
        messageListView.setCellFactory(listView -> new ListCell<>() {
            private final Label metaLabel = new Label();
            private final Label bodyLabel = new Label();
            private final VBox container = new VBox(2.0, metaLabel, bodyLabel);

            {
                container.getStyleClass().add("irc-message-cell");
                metaLabel.getStyleClass().add("irc-message-meta");
                bodyLabel.getStyleClass().add("irc-message-body");
                bodyLabel.setWrapText(true);
                bodyLabel.prefWidthProperty().bind(listView.widthProperty().subtract(42));
            }

            @Override
            protected void updateItem(IrcMessageEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                String sender = item.ownMessage() ? "You" : (item.sender() == null || item.sender().isBlank() ? "*" : item.sender());
                metaLabel.setText(String.format("[%s] %s", MESSAGE_TIME_FORMAT.format(item.timestamp()), sender));
                bodyLabel.setText(item.text());
                container.setStyle(item.ownMessage() ? "-fx-background-color: rgba(30,116,198,0.12); -fx-background-radius: 6; -fx-padding: 6;"
                        : "-fx-padding: 6;");
                setGraphic(container);
                setText(null);
            }
        });
    }

    private List<IrcMessageEntry> filterMessages(List<IrcMessageEntry> history) {
        if (!suppressJoinLeaveNoiseCheckBox.isSelected()) {
            return history;
        }

        return history.stream()
                .filter(entry -> entry.notificationType() != IrcChannelNotificationType.JOIN
                        && entry.notificationType() != IrcChannelNotificationType.PART
                        && entry.notificationType() != IrcChannelNotificationType.QUIT
                        && entry.notificationType() != IrcChannelNotificationType.NICK_CHANGE)
                .toList();
    }

    private List<String> filterUsers(List<String> channelUsers) {
        String filter = userSearchField.getText() == null ? "" : userSearchField.getText().trim().toLowerCase(Locale.ROOT);
        return channelUsers.stream()
                .sorted((left, right) -> {
                    int moderatorCompare = Boolean.compare(!isModerator(left), !isModerator(right));
                    if (moderatorCompare != 0) {
                        return moderatorCompare;
                    }
                    return stripPrefix(left).compareToIgnoreCase(stripPrefix(right));
                })
                .filter(user -> filter.isBlank() || stripPrefix(user).toLowerCase(Locale.ROOT).contains(filter))
                .toList();
    }

    private boolean isModerator(String nick) {
        return !nick.isBlank() && "@&~%".indexOf(nick.charAt(0)) >= 0;
    }

    private String stripPrefix(String nick) {
        if (nick == null) {
            return "";
        }
        String normalized = nick.trim();
        if (!normalized.isBlank() && "@&~%+".indexOf(normalized.charAt(0)) >= 0) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private void selectChannel(String channelName) {
        for (IrcChannelSnapshot snapshot : channels) {
            if (snapshot.name().equalsIgnoreCase(channelName)) {
                channelListView.getSelectionModel().select(snapshot);
                return;
            }
        }
    }

    private String resolveModeratorNickname(LocalPreferences.TabIrcChat tab) {
        Optional<String> tokenUsername = tokenService.getPreferredUsername();
        if (tokenUsername.isPresent() && !tokenUsername.get().isBlank()) {
            return tokenUsername.get();
        }
        if (fafApiCommunicationService.getMeResult() != null && fafApiCommunicationService.getMeResult().getUserName() != null
                && !fafApiCommunicationService.getMeResult().getUserName().isBlank()) {
            return fafApiCommunicationService.getMeResult().getUserName();
        }
        return tab.getNickname() == null ? "" : tab.getNickname();
    }

    private String colorForStatus(IrcConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> "#49b675";
            case CONNECTING, AUTHENTICATING, RECONNECTING, DISCONNECTING -> "#d0a733";
            case NICKNAME_CONFLICT, ERROR -> "#db5d5d";
            case DISCONNECTED -> "#8f96a3";
        };
    }

    private boolean hasKickTarget() {
        if (!fafApiCommunicationService.hasPermission(GroupPermission.ADMIN_KICK_SERVER)) {
            return false;
        }

        if (hasManualKickInput()) {
            return true;
        }

        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser == null || selectedUser.isBlank()) {
            return false;
        }

        String selectedLogin = stripPrefix(selectedUser);
        return !selectedLogin.equalsIgnoreCase(resolveModeratorNickname(localPreferences.getTabIrcChat()));
    }

    private void kickSelectedUser(KickTarget target) {
        if (!hasKickTarget()) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        KickRequest targetRequest;
        try {
            targetRequest = resolveKickRequest();
        } catch (IllegalStateException e) {
            showAlert(Alert.AlertType.WARNING, "Kick Target Invalid", e.getMessage());
            return;
        }

        confirm.setTitle("Confirm Kick");
        confirm.setHeaderText(target.confirmHeader(targetRequest.displayName()));
        confirm.setContentText(target.confirmBody(targetRequest.displayName()));

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        setModerationActionInProgress(true);
        CompletableFuture
                .runAsync(() -> executeKick(target, targetRequest))
                .whenComplete((unused, throwable) -> Platform.runLater(() -> {
                    setModerationActionInProgress(false);
                    if (throwable == null) {
                        showAlert(Alert.AlertType.INFORMATION, "Kick Sent",
                                target.successMessage(targetRequest.displayName()));
                    } else {
                        log.error("Failed to {} for {}", target.actionName(), targetRequest.displayName(), throwable);
                        showAlert(Alert.AlertType.ERROR, "Kick Failed",
                                "Failed to " + target.actionName() + " for " + targetRequest.displayName() + ".\n\n" + rootCauseMessage(throwable));
                    }
                }));
    }

    private void executeKick(KickTarget target, KickRequest request) {
        switch (target) {
            case GAME -> lobbyModerationService.kickFromGame(request.playerId());
            case CLIENT -> lobbyModerationService.kickFromClient(request.playerId());
            case GAME_AND_CLIENT -> lobbyModerationService.kickFromGameAndClient(request.playerId());
        }
    }

    private PlayerFX findPlayerById(String playerId) {
        List<PlayerFX> matches = userService.findUsersByAttribute("id", playerId);
        return matches.stream()
                .filter(player -> player.getId() != null && player.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No FAF player found for user ID: " + playerId));
    }

    private PlayerFX findPlayerByLoginOrName(String loginOrName) {
        List<PlayerFX> loginMatches = userService.findUsersByAttribute("login", loginOrName);
        Optional<PlayerFX> exactLoginMatch = loginMatches.stream()
                .filter(player -> player.getLogin() != null && player.getLogin().equalsIgnoreCase(loginOrName))
                .findFirst();
        if (exactLoginMatch.isPresent()) {
            return exactLoginMatch.get();
        }

        List<PlayerFX> nameMatches = userService.findUsersByAttribute("names.name", loginOrName).stream()
                .filter(player -> player.getNames().stream().anyMatch(name ->
                        name.getName() != null && name.getName().equalsIgnoreCase(loginOrName)))
                .toList();
        if (nameMatches.size() == 1) {
            return nameMatches.getFirst();
        }
        if (nameMatches.size() > 1) {
            throw new IllegalStateException("Multiple FAF players match that name. Enter the user ID or exact username instead.");
        }

        throw new IllegalStateException("No FAF player found for username or name: " + loginOrName);
    }

    private KickRequest resolveKickRequest() {
        String manualLookup = normalizeKickInput(kickLookupField.getText());

        if (!manualLookup.isBlank()) {
            PlayerFX player = isNumeric(manualLookup)
                    ? findPlayerById(manualLookup)
                    : findPlayerByLoginOrName(manualLookup);

            int playerId = parsePlayerId(player);
            String login = normalizeKickInput(player.getLogin());
            ensureNotSelf(playerId, login.isBlank() ? manualLookup : login);

            return new KickRequest(playerId, login, formatKickDisplay(login, playerId));
        }

        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser == null || selectedUser.isBlank()) {
            throw new IllegalStateException("Select a user or enter a user ID or username.");
        }

        String selectedLogin = stripPrefix(selectedUser);
        PlayerFX player = findPlayerByLoginOrName(selectedLogin);
        int playerId = parsePlayerId(player);
        ensureNotSelf(playerId, selectedLogin);
        String login = normalizeKickInput(player.getLogin());
        return new KickRequest(playerId, login.isBlank() ? selectedLogin : login, formatKickDisplay(login.isBlank() ? selectedLogin : login, playerId));
    }

    private boolean hasManualKickInput() {
        return !normalizeKickInput(kickLookupField.getText()).isBlank();
    }

    private String normalizeKickInput(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isNumeric(String value) {
        return !value.isBlank() && value.chars().allMatch(Character::isDigit);
    }

    private String formatKickDisplay(String login, int playerId) {
        return login == null || login.isBlank()
                ? "user ID " + playerId
                : login + " [id " + playerId + "]";
    }

    private void ensureNotSelf(int playerId, String login) {
        if (login != null && !login.isBlank()
                && login.equalsIgnoreCase(resolveModeratorNickname(localPreferences.getTabIrcChat()))) {
            throw new IllegalStateException("You cannot kick your own moderator account.");
        }

        if (fafApiCommunicationService.getMeResult() != null
                && fafApiCommunicationService.getMeResult().getId() != null
                && fafApiCommunicationService.getMeResult().getId().equals(String.valueOf(playerId))) {
            throw new IllegalStateException("You cannot kick your own moderator account.");
        }
    }

    private int parsePlayerId(PlayerFX player) {
        try {
            return Integer.parseInt(player.getId());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid FAF player id: " + player.getId(), e);
        }
    }

    private void setModerationActionInProgress(boolean moderationActionInProgress) {
        this.moderationActionInProgress = moderationActionInProgress;
        updateConnectionState(ircClient.getConnectionState());
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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

    private record KickRequest(int playerId, String login, String displayName) {
    }

    private enum KickTarget {
        GAME("kick from game"),
        CLIENT("kick from client"),
        GAME_AND_CLIENT("kick from game and client");

        private final String actionName;

        KickTarget(String actionName) {
            this.actionName = actionName;
        }

        private String actionName() {
            return actionName;
        }

        private String confirmHeader(String login) {
            return switch (this) {
                case GAME -> "Kick " + login + " from the current FAF game?";
                case CLIENT -> "Kick " + login + " from the FAF client?";
                case GAME_AND_CLIENT -> "Kick " + login + " from both the FAF game and client?";
            };
        }

        private String confirmBody(String login) {
            String lobbyAuthHint = "\n\nIf the separate FAF lobby authorization is not stored yet, the client will open a browser login once. "
                    + "This is required because kick actions use FAF lobby permissions, not IRC.";
            return switch (this) {
                case GAME -> "This will close the player's current game session if they are in one." + lobbyAuthHint;
                case CLIENT -> "This will remove " + login + " from the FAF client/lobby." + lobbyAuthHint;
                case GAME_AND_CLIENT -> "This will attempt both actions in sequence: close the game session, then remove the client from FAF." + lobbyAuthHint;
            };
        }

        private String successMessage(String login) {
            return switch (this) {
                case GAME -> "Kick-from-game request sent for " + login + ".";
                case CLIENT -> "Kick-from-client request sent for " + login + ".";
                case GAME_AND_CLIENT -> "Kick-from-game-and-client request sent for " + login + ".";
            };
        }
    }
}
