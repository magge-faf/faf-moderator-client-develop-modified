package com.faforever.moderatorclient.irc;

import com.faforever.moderatorclient.api.FafUserCommunicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.engio.mbassy.listener.Handler;
import org.kitteh.irc.client.library.element.MessageTag;
import org.kitteh.irc.client.library.element.MessageTag.MsgId;
import org.kitteh.irc.client.library.element.MessageTag.Time;
import org.kitteh.irc.client.library.event.batch.ClientBatchEndEvent;
import org.kitteh.irc.client.library.event.batch.ClientBatchStartEvent;
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.event.channel.ChannelModeEvent;
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent;
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent;
import org.kitteh.irc.client.library.event.channel.ChannelUsersUpdatedEvent;
import org.kitteh.irc.client.library.event.client.ClientNegotiationCompleteEvent;
import org.kitteh.irc.client.library.event.client.ClientReceiveNumericEvent;
import org.kitteh.irc.client.library.event.helper.ClientReceiveServerMessageEvent;
import org.kitteh.irc.client.library.event.client.NickRejectedEvent;
import org.kitteh.irc.client.library.event.connection.ClientConnectionClosedEvent;
import org.kitteh.irc.client.library.event.connection.ClientConnectionEndedEvent;
import org.kitteh.irc.client.library.event.connection.ClientConnectionEstablishedEvent;
import org.kitteh.irc.client.library.event.helper.MessageEvent;
import org.kitteh.irc.client.library.event.helper.ServerMessageEvent;
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent;
import org.kitteh.irc.client.library.event.user.UserNickChangeEvent;
import org.kitteh.irc.client.library.event.user.UserQuitEvent;
import org.kitteh.irc.client.library.element.mode.ChannelUserMode;
import org.kitteh.irc.client.library.element.mode.ModeStatus.Action;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class IrcClientService implements IrcClient, DisposableBean {
    private static final long INITIAL_RECONNECT_DELAY_MILLIS = 1_000L;
    private static final long MAX_RECONNECT_DELAY_MILLIS = 30_000L;
    private static final int HISTORY_PAGE_LIMIT = 500;
    private static final int HISTORY_SANITY_LIMIT = 20_000;
    private static final int HISTORY_SINCE_TIMEOUT_SECONDS = 60;

    private final ConnectionManager connectionManager = new ConnectionManager();
    private final MessageParser messageParser = new MessageParser();
    private final EventDispatcher eventDispatcher = new EventDispatcher();
    private final ChannelManager channelManager = new ChannelManager();
    private final UserManager userManager = new UserManager();
    private final FafUserCommunicationService fafUserCommunicationService;
    private final ExecutorService connectExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "irc-connect");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicReference<IrcConnectionState> connectionState = new AtomicReference<>(IrcConnectionState.disconnected());
    private final AtomicReference<IrcConfiguration> configurationRef = new AtomicReference<>();
    private final Set<String> desiredChannels = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean manualDisconnect = new AtomicBoolean(true);
    private final AtomicInteger reconnectAttempt = new AtomicInteger();
    private final AtomicInteger nicknameConflictCounter = new AtomicInteger();
    private final AtomicInteger connectRequestCounter = new AtomicInteger();
    private final Map<String, HistoryRequestState> pendingHistoryRequests = new ConcurrentHashMap<>();
    private final Map<String, HistoryRequestState> activeHistoryRequests = new ConcurrentHashMap<>();
    private final Set<String> historicalBatchReferences = ConcurrentHashMap.newKeySet();
    private final Map<String, PendingHistoryBatch> pendingHistoryBatches = new ConcurrentHashMap<>();

    @Override
    public void connect() {
        IrcConfiguration configuration = configurationRef.get();
        if (configuration != null) {
            connect(configuration);
        }
    }

    @Override
    public synchronized void connect(IrcConfiguration configuration) {
        if (configuration.nickname().isBlank()) {
            updateConnectionState(IrcConnectionStatus.ERROR, configuration, "", "IRC nickname is required", reconnectAttempt.get(), 0L);
            return;
        }

        configurationRef.set(configuration);
        desiredChannels.clear();
        desiredChannels.addAll(configuration.autoJoinChannels());
        channelManager.ensureChannels(desiredChannels);
        userManager.clear();
        manualDisconnect.set(false);
        reconnectAttempt.set(0);
        nicknameConflictCounter.set(0);
        int connectRequest = connectRequestCounter.incrementAndGet();

        updateConnectionState(IrcConnectionStatus.AUTHENTICATING, configuration, "",
                "Requesting FAF IRC token for " + configuration.nickname(), 0, 0L);
        CompletableFuture.supplyAsync(fafUserCommunicationService::getIrcChatToken, connectExecutor)
                .thenAccept(token -> onIrcTokenReady(connectRequest, configuration, token))
                .exceptionally(throwable -> {
                    handleTokenFetchFailure(connectRequest, configuration, throwable);
                    return null;
                });
    }

    @Override
    public synchronized void disconnect() {
        manualDisconnect.set(true);
        connectRequestCounter.incrementAndGet();
        failOutstandingHistoryRequests("IRC disconnected");
        Optional<IrcConfiguration> configuration = getConfiguration();
        configuration.ifPresent(config -> updateConnectionState(IrcConnectionStatus.DISCONNECTING, config,
                connectionState.get().currentNickname(), "Disconnecting", reconnectAttempt.get(), 0L));
        connectionManager.disconnect("Disconnect requested by user");
        clearPresence();
        configuration.ifPresent(config -> updateConnectionState(IrcConnectionStatus.DISCONNECTED, config,
                "", "Disconnected", 0, 0L));
    }

    @Override
    public void joinChannel(String channel) {
        String normalizedChannel = IrcConfiguration.normalizeChannel(channel);
        if (normalizedChannel.isBlank()) {
            return;
        }

        desiredChannels.add(normalizedChannel);
        channelManager.ensureChannel(normalizedChannel);

        if (connectionState.get().status() == IrcConnectionStatus.CONNECTED) {
            connectionManager.addChannel(normalizedChannel);
        }
    }

    @Override
    public void leaveChannel(String channel) {
        String normalizedChannel = normalizeTarget(channel);
        if (normalizedChannel.isBlank()) {
            return;
        }

        if (isPrivateConversation(normalizedChannel)) {
            channelManager.removeChannel(normalizedChannel);
            userManager.replaceUsers(normalizedChannel, List.of());
            dispatchUserList(normalizedChannel);
            return;
        }

        desiredChannels.remove(normalizedChannel);
        connectionManager.removeChannel(normalizedChannel, "Leaving channel");
        channelManager.markLeft(normalizedChannel);
        userManager.replaceUsers(normalizedChannel, List.of());
        dispatchUserList(normalizedChannel);
    }

    @Override
    public void openPrivateConversation(String username) {
        String conversation = toPrivateConversation(username);
        if (conversation.isBlank()) {
            return;
        }

        channelManager.ensureChannel(conversation);
    }

    @Override
    public void sendMessage(String channel, String message) {
        String normalizedChannel = normalizeTarget(channel);
        if (normalizedChannel.isBlank() || message == null || message.isBlank()) {
            return;
        }
        String trimmedMessage = message.trim();
        channelManager.ensureChannel(normalizedChannel);

        IrcConnectionState currentState = connectionState.get();
        IrcConfiguration configuration = configurationRef.get();
        String nickname;
        if (currentState != null && !currentState.currentNickname().isBlank()) {
            nickname = currentState.currentNickname();
        } else if (configuration != null) {
            nickname = configuration.nickname();
        } else {
            nickname = "unknown";
        }

        IrcChannelMessageEvent localEvent = new IrcChannelMessageEvent(
                normalizedChannel,
                nickname,
                trimmedMessage,
                null,
                IrcMessageKind.CHAT,
                true,
                false,
                Instant.now()
        );
        channelManager.addMessage(localEvent, true);
        eventDispatcher.dispatch(localEvent);
        connectionManager.sendMessage(toWireTarget(normalizedChannel), trimmedMessage);
    }

    @Override
    public void markChannelRead(String channel) {
        String normalizedChannel = normalizeTarget(channel);
        if (normalizedChannel.isBlank()) {
            return;
        }
        channelManager.markRead(normalizedChannel);
    }

    @Override
    public CompletableFuture<IrcHistoryLoadResult> requestRecentHistory(String target, int limit) {
        String normalizedTarget = normalizeTarget(target);
        if (normalizedTarget.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Select a channel first."));
        }
        if (isPrivateConversation(normalizedTarget)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("IRC history is only supported for channels."));
        }
        if (connectionState.get().status() != IrcConnectionStatus.CONNECTED) {
            return CompletableFuture.failedFuture(new IllegalStateException("IRC must be connected before loading history."));
        }

        CompletableFuture<IrcHistoryLoadResult> future = new CompletableFuture<>();
        HistoryRequestState request = new HistoryRequestState(
                normalizedTarget,
                null,
                Math.max(1, Math.min(limit, HISTORY_PAGE_LIMIT)),
                0,
                future,
                Mode.RECENT,
                false
        );
        if (pendingHistoryRequests.putIfAbsent(normalizedTarget, request) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("A history request is already running for " + normalizedTarget + "."));
        }

        log.debug("IRC history request: recent target={} limit={}", normalizedTarget, request.pageLimit());
        connectionManager.sendRawLine("CHATHISTORY LATEST " + toWireTarget(normalizedTarget) + " * " + request.pageLimit());
        return future.orTimeout(20, TimeUnit.SECONDS).whenComplete((unused, throwable) -> clearHistoryRequest(normalizedTarget));
    }

    @Override
    public CompletableFuture<IrcHistoryLoadResult> requestHistorySince(String target, Instant since) {
        String normalizedTarget = normalizeTarget(target);
        if (normalizedTarget.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Select a channel first."));
        }
        if (isPrivateConversation(normalizedTarget)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("IRC history is only supported for channels."));
        }
        if (since == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("A start time is required."));
        }
        if (connectionState.get().status() != IrcConnectionStatus.CONNECTED) {
            return CompletableFuture.failedFuture(new IllegalStateException("IRC must be connected before loading history."));
        }

        CompletableFuture<IrcHistoryLoadResult> future = new CompletableFuture<>();
        HistoryRequestState request = new HistoryRequestState(
                normalizedTarget,
                since.truncatedTo(ChronoUnit.MILLIS),
                HISTORY_PAGE_LIMIT,
                0,
                future,
                Mode.SINCE,
                false
        );
        if (pendingHistoryRequests.putIfAbsent(normalizedTarget, request) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("A history request is already running for " + normalizedTarget + "."));
        }

        log.debug("IRC history request: since target={} since={} limit={}", normalizedTarget, request.since(), request.pageLimit());
        connectionManager.sendRawLine("CHATHISTORY LATEST " + toWireTarget(normalizedTarget) + " * " + request.pageLimit());
        return future.orTimeout(HISTORY_SINCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((unused, throwable) -> clearHistoryRequest(normalizedTarget));
    }

    @Override
    public IrcConnectionState getConnectionState() {
        return connectionState.get();
    }

    @Override
    public Optional<IrcConfiguration> getConfiguration() {
        return Optional.ofNullable(configurationRef.get());
    }

    @Override
    public Optional<IrcChannelSnapshot> getChannelSnapshot(String channel) {
        return channelManager.snapshot(normalizeTarget(channel), userManager);
    }

    @Override
    public List<IrcChannelSnapshot> getChannelSnapshots() {
        return channelManager.snapshots(userManager);
    }

    @Override
    public IrcListenerRegistration addMessageListener(Consumer<IrcChannelMessageEvent> listener) {
        return eventDispatcher.addMessageListener(listener);
    }

    @Override
    public IrcListenerRegistration addConnectionListener(Consumer<IrcConnectionEvent> listener) {
        return eventDispatcher.addConnectionListener(listener);
    }

    @Override
    public IrcListenerRegistration addUserListListener(Consumer<IrcUserListEvent> listener) {
        return eventDispatcher.addUserListListener(listener);
    }

    @Override
    public IrcListenerRegistration addEventListener(Consumer<IrcEvent> listener) {
        return eventDispatcher.addEventListener(listener);
    }

    @Handler
    public void onConnectionEstablished(ClientConnectionEstablishedEvent event) {
        getConfiguration().ifPresent(configuration ->
                updateConnectionState(reconnectAttempt.get() > 0 ? IrcConnectionStatus.RECONNECTING : IrcConnectionStatus.CONNECTING,
                        configuration,
                        event.getClient().getNick(),
                        "Connected to server, completing SASL negotiation",
                        reconnectAttempt.get(),
                        0L));
    }

    @Handler
    public void onNegotiationComplete(ClientNegotiationCompleteEvent event) {
        getConfiguration().ifPresent(configuration -> {
            nicknameConflictCounter.set(0);
            reconnectAttempt.set(0);
            updateConnectionState(IrcConnectionStatus.CONNECTED, configuration, event.getClient().getNick(),
                    "Connected as " + event.getClient().getNick(),
                    0, 0L);
            event.getClient().commands().capabilityRequest().enable("draft/chathistory").execute();
            joinDesiredChannels();
        });
    }

    @Handler
    public void onNickRejected(NickRejectedEvent event) {
        IrcConfiguration configuration = configurationRef.get();
        if (configuration == null) {
            return;
        }

        int conflictCount = nicknameConflictCounter.incrementAndGet();
        String baseNick = configuration.nickname();
        String fallbackNick = baseNick + "_" + conflictCount;
        event.setNewNick(fallbackNick);

        updateConnectionState(IrcConnectionStatus.NICKNAME_CONFLICT, configuration, fallbackNick,
                "Nickname " + baseNick + " is already in use, retrying as " + fallbackNick,
                reconnectAttempt.get(), 0L);
    }

    @Handler
    public void onChannelMessage(ChannelMessageEvent event) {
        IrcChannelMessageEvent parsedEvent = messageParser.parseMessage(event, connectionState.get().currentNickname());
        IrcChannelMessageEvent resolvedEvent = historicalMessageEvent(parsedEvent, event);
        try {
            if (IrcNoiseFilter.isHistoryServerMessage(resolvedEvent.author())) {
                log.debug("IRC history replay ignored HistServ channel={} message={}", resolvedEvent.channel(), resolvedEvent.message());
                return;
            }
            if (resolvedEvent.historical()) {
                log.debug("IRC history replay chat channel={} author={} messageId={} text={}",
                        resolvedEvent.channel(),
                        resolvedEvent.author(),
                        resolvedEvent.messageId(),
                        abbreviateForLog(resolvedEvent.message()));
            }
            channelManager.addMessage(resolvedEvent, !resolvedEvent.historical());
            eventDispatcher.dispatch(resolvedEvent);
        } finally {
            finalizeHistoryReplayProgress(event);
        }
    }

    @Handler
    public void onHistoryBatchStart(ClientBatchStartEvent event) {
        if (!"chathistory".equalsIgnoreCase(event.getReferenceTag().getType()) || event.getReferenceTag().getParameters().isEmpty()) {
            return;
        }

        String target = normalizeTarget(event.getReferenceTag().getParameters().getFirst());
        HistoryRequestState pending = pendingHistoryRequests.remove(target);
        if (pending == null) {
            return;
        }

        boolean reachedServerEnd = event.getSource().getTag("draft/chathistory-end").isPresent();
        HistoryRequestState active = pending.withReachedServerEnd(reachedServerEnd);
        activeHistoryRequests.put(event.getReferenceTag().getReferenceTag(), active);
        historicalBatchReferences.add(event.getReferenceTag().getReferenceTag());
        log.debug("IRC history batch start: ref={} target={} events={} reachedServerEnd={}",
                event.getReferenceTag().getReferenceTag(),
                target,
                event.getReferenceTag().getEvents().size(),
                reachedServerEnd);
    }

    @Handler
    public void onHistoryBatchEnd(ClientBatchEndEvent event) {
        String batchReference = event.getReferenceTag().getReferenceTag();
        HistoryRequestState active = activeHistoryRequests.get(batchReference);
        if (active == null) {
            return;
        }

        HistoryPage page = summarizeHistoryPage(event);
        log.debug("IRC history batch end: ref={} target={} eventCount={} earliestSelector={} earliestTimestamp={}",
                batchReference,
                active.target(),
                page.visibleMessageCount(),
                page.earliestSelector(),
                page.earliestTimestamp());
        if (page.replayMessageCount() == 0) {
            finalizeHistoryBatch(batchReference, active, page);
            return;
        }
        pendingHistoryBatches.put(batchReference, new PendingHistoryBatch(active, page, new AtomicInteger(page.replayMessageCount())));
    }

    private void finalizeHistoryBatch(String batchReference, HistoryRequestState active, HistoryPage page) {
        pendingHistoryBatches.remove(batchReference);
        historicalBatchReferences.remove(batchReference);
        activeHistoryRequests.remove(batchReference);

        int totalLoaded = active.totalLoaded() + page.visibleMessageCount();

        if (active.mode() == Mode.RECENT) {
            log.debug("IRC history request complete: target={} loaded={} mode=recent reachedServerEnd={}",
                    active.target(), totalLoaded, active.reachedServerEnd());
            active.future().complete(new IrcHistoryLoadResult(active.target(), totalLoaded, null, false, active.reachedServerEnd()));
            return;
        }

        boolean reachedRequestedStart = page.earliestTimestamp() != null && !page.earliestTimestamp().isAfter(active.since());
        boolean stop = page.replayMessageCount() == 0
                || reachedRequestedStart
                || active.reachedServerEnd()
                || totalLoaded >= HISTORY_SANITY_LIMIT
                || page.earliestSelector() == null;

        if (stop) {
            log.debug("IRC history request complete: target={} loaded={} mode=since reachedRequestedStart={} reachedServerEnd={}",
                    active.target(), totalLoaded, reachedRequestedStart, active.reachedServerEnd());
            active.future().complete(new IrcHistoryLoadResult(
                    active.target(),
                    totalLoaded,
                    active.since(),
                    reachedRequestedStart,
                    active.reachedServerEnd()
            ));
            return;
        }

        HistoryRequestState nextPage = active.withTotalLoaded(totalLoaded).withReachedServerEnd(false);
        pendingHistoryRequests.put(active.target(), nextPage);
        log.debug("IRC history request continue: target={} loaded={} before={}", active.target(), totalLoaded, page.earliestSelector());
        connectionManager.sendRawLine("CHATHISTORY BEFORE " + toWireTarget(active.target()) + " " + page.earliestSelector() + " " + active.pageLimit());
    }

    private HistoryPage summarizeHistoryPage(ClientBatchEndEvent event) {
        List<ClientReceiveServerMessageEvent> messageEvents = event.getReferenceTag().getEvents().stream()
                .filter(ClientReceiveServerMessageEvent.class::isInstance)
                .map(ClientReceiveServerMessageEvent.class::cast)
                .filter(messageEvent -> "PRIVMSG".equalsIgnoreCase(messageEvent.getCommand()))
                .filter(messageEvent -> messageEvent.getParameters().size() >= 2)
                .toList();

        if (messageEvents.isEmpty()) {
            return new HistoryPage(0, 0, null, null);
        }

        ClientReceiveServerMessageEvent earliest = messageEvents.getFirst();
        Instant earliestTimestamp = extractTimestamp(earliest).orElse(null);
        String earliestSelector = earliest.getTag("msgid", MsgId.class)
                .map(msgId -> "msgid=" + msgId.getId())
                .or(() -> extractTimestamp(earliest).map(this::formatTimestampSelector))
                .orElse(null);

        int visibleMessageCount = (int) messageEvents.stream()
                .filter(this::isVisibleHistoryMessage)
                .count();

        return new HistoryPage(messageEvents.size(), visibleMessageCount, earliestSelector, earliestTimestamp);
    }

    private String formatTimestampSelector(Instant instant) {
        return "timestamp=" + instant.truncatedTo(ChronoUnit.MILLIS);
    }

    private IrcChannelMessageEvent historicalMessageEvent(IrcChannelMessageEvent event, ServerMessageEvent sourceEvent) {
        boolean historical = isHistoryBatchEvent(sourceEvent);
        if (!historical) {
            return event;
        }
        return new IrcChannelMessageEvent(
                event.channel(),
                event.author(),
                event.message(),
                event.messageId(),
                event.kind(),
                event.ownMessage(),
                true,
                event.timestamp()
        );
    }

    private boolean isHistoryBatchEvent(ServerMessageEvent event) {
        return event.getTag("batch")
                .flatMap(MessageTag::getValue)
                .map(historicalBatchReferences::contains)
                .orElse(false);
    }

    private Optional<Instant> extractTimestamp(ServerMessageEvent event) {
        return event.getTag("time", Time.class).map(Time::getTime);
    }

    @Handler
    public void onPrivateMessage(PrivateMessageEvent event) {
        String conversation = toPrivateConversation(event.getActor().getNick());
        channelManager.ensureChannel(conversation);
        IrcChannelMessageEvent parsedEvent = new IrcChannelMessageEvent(
                conversation,
                event.getActor().getNick(),
                event.getMessage(),
                event.getTag("msgid", MsgId.class).map(MsgId::getId).orElse(null),
                IrcMessageKind.CHAT,
                false,
                isHistoryBatchEvent(event),
                extractTimestamp(event).orElse(Instant.now())
        );
        try {
            channelManager.addMessage(parsedEvent, !parsedEvent.historical());
            eventDispatcher.dispatch(parsedEvent);
        } finally {
            finalizeHistoryReplayProgress(event);
        }
    }

    @Handler
    public void onChannelJoin(ChannelJoinEvent event) {
        String channel = event.getChannel().getName();
        if (event.getClient().isUser(event.getUser())) {
            channelManager.markJoined(channel);
            requestChannelUsers(channel);
        }
        userManager.addUser(channel, event.getUser().getNick());
        IrcChannelNotificationEvent parsedEvent = messageParser.parseJoin(event);
        channelManager.addNotification(channel, parsedEvent.actor(), parsedEvent.message(), parsedEvent.type(), parsedEvent.timestamp(), true);
        eventDispatcher.dispatch(parsedEvent);
        dispatchUserList(channel);
    }

    @Handler
    public void onChannelPart(ChannelPartEvent event) {
        String channel = event.getChannel().getName();
        if (event.getClient().isUser(event.getUser())) {
            channelManager.markLeft(channel);
            userManager.replaceUsers(channel, List.of());
        } else {
            userManager.removeUser(channel, event.getUser().getNick());
        }
        IrcChannelNotificationEvent parsedEvent = messageParser.parsePart(event);
        channelManager.addNotification(channel, parsedEvent.actor(), parsedEvent.message(), parsedEvent.type(), parsedEvent.timestamp(), true);
        eventDispatcher.dispatch(parsedEvent);
        dispatchUserList(channel);
    }

    @Handler
    public void onChannelTopic(ChannelTopicEvent event) {
        IrcTopicEvent parsedEvent = messageParser.parseTopic(event);
        channelManager.updateTopic(parsedEvent.channel(), parsedEvent.topic());
        channelManager.addNotification(parsedEvent.channel(), parsedEvent.setBy(), "Topic: " + parsedEvent.topic(),
                IrcChannelNotificationType.TOPIC, parsedEvent.timestamp(), true);
        eventDispatcher.dispatch(parsedEvent);
    }

    @Handler
    public void onChannelUsersUpdated(ChannelUsersUpdatedEvent event) {
        String channel = event.getChannel().getName();
        userManager.replaceUsers(channel, event.getChannel().getNicknames());
        dispatchUserList(channel);
    }

    @Handler
    public void onChannelModeChanged(ChannelModeEvent event) {
        event.getStatusList().getAll().forEach(channelModeStatus -> channelModeStatus.getParameter().ifPresent(username -> {
            if (channelModeStatus.getMode() instanceof ChannelUserMode channelUserMode) {
                String prefix = String.valueOf(channelUserMode.getNickPrefix());
                if (Action.ADD.equals(channelModeStatus.getAction()) && isModeratorPrefix(prefix)) {
                    userManager.promoteUserPrefix(event.getChannel().getName(), username, prefix);
                    dispatchUserList(event.getChannel().getName());
                } else if (Action.REMOVE.equals(channelModeStatus.getAction()) && isModeratorPrefix(prefix)) {
                    userManager.demoteUserPrefix(event.getChannel().getName(), username);
                    dispatchUserList(event.getChannel().getName());
                }
            }
        }));
    }

    @Handler
    public void onUserQuit(UserQuitEvent event) {
        List<String> channels = userManager.channelsContaining(event.getUser().getNick());
        userManager.removeUserEverywhere(event.getUser().getNick());
        for (String channel : channels) {
            IrcChannelNotificationEvent parsedEvent = messageParser.parseQuit(channel, event);
            channelManager.addNotification(channel, parsedEvent.actor(), parsedEvent.message(), parsedEvent.type(), parsedEvent.timestamp(), true);
            eventDispatcher.dispatch(parsedEvent);
            dispatchUserList(channel);
        }
    }

    @Handler
    public void onUserNickChange(UserNickChangeEvent event) {
        Set<String> channels = new LinkedHashSet<>(event.getOldUser().getChannels());
        channels.addAll(event.getNewUser().getChannels());
        userManager.renameUser(event.getOldUser().getNick(), event.getNewUser().getNick());
        for (String channel : channels) {
            IrcChannelNotificationEvent parsedEvent = messageParser.parseNickChange(channel, event);
            channelManager.addNotification(channel, parsedEvent.actor(), parsedEvent.message(), parsedEvent.type(), parsedEvent.timestamp(), true);
            eventDispatcher.dispatch(parsedEvent);
            dispatchUserList(channel);
        }

        IrcConnectionState state = connectionState.get();
        if (state.currentNickname().equalsIgnoreCase(event.getOldUser().getNick())) {
            getConfiguration().ifPresent(configuration ->
                    updateConnectionState(state.status(), configuration, event.getNewUser().getNick(), state.statusMessage(),
                            state.reconnectAttempt(), state.nextReconnectDelayMillis()));
        }
    }

    @Handler
    public void onNumeric(ClientReceiveNumericEvent event) {
        messageParser.parseWhoUser(event).ifPresent(whoUser -> {
            if (desiredChannels.contains(whoUser.channel())) {
                userManager.addWhoUser(whoUser.channel(), whoUser.nick());
            }
        });
        messageParser.parseWhoComplete(event).ifPresent(channel -> {
            if (desiredChannels.contains(channel)) {
                userManager.completeWhoQuery(channel);
                dispatchUserList(channel);
            }
        });
        messageParser.parseJoinFailure(event).ifPresent(failure -> {
            String channel = IrcConfiguration.normalizeChannel(failure.channel());
            String actor = channel.isBlank() ? "server" : channel;
            Instant timestamp = Instant.now();
            channelManager.ensureChannel(channel.isBlank() ? IrcConfiguration.DEFAULT_CHANNEL : channel);
            channelManager.addNotification(channel.isBlank() ? IrcConfiguration.DEFAULT_CHANNEL : channel,
                    actor,
                    failure.message(),
                    IrcChannelNotificationType.ERROR,
                    timestamp,
                    true);
            eventDispatcher.dispatch(new IrcChannelNotificationEvent(
                    channel.isBlank() ? IrcConfiguration.DEFAULT_CHANNEL : channel,
                    IrcChannelNotificationType.ERROR,
                    actor,
                    null,
                    failure.message(),
                    timestamp
            ));
        });
    }

    @Handler
    public void onConnectionEnded(ClientConnectionEndedEvent event) {
        IrcConfiguration configuration = configurationRef.get();
        if (configuration == null) {
            return;
        }

        failOutstandingHistoryRequests("IRC connection ended");
        clearPresence();

        if (manualDisconnect.get()) {
            event.setAttemptReconnect(false);
            reconnectAttempt.set(0);
            updateConnectionState(IrcConnectionStatus.DISCONNECTED, configuration, "", "Disconnected", 0, 0L);
            return;
        }

        int attempt = reconnectAttempt.incrementAndGet();
        long delayMillis = Math.min(MAX_RECONNECT_DELAY_MILLIS, INITIAL_RECONNECT_DELAY_MILLIS << Math.min(attempt - 1, 5));
        event.setAttemptReconnect(true);
        event.setReconnectionDelay((int) delayMillis);

        String message = buildReconnectMessage(event, delayMillis, attempt);
        updateConnectionState(IrcConnectionStatus.RECONNECTING, configuration, "", message, attempt, delayMillis);
    }

    @Handler
    public void onConnectionClosed(ClientConnectionClosedEvent event) {
        if (manualDisconnect.get()) {
            return;
        }
        event.getLastMessage().ifPresent(lastMessage -> log.warn("IRC server closed connection: {}", lastMessage));
    }

    @Override
    public void destroy() {
        disconnect();
        connectExecutor.shutdownNow();
    }

    private void joinDesiredChannels() {
        desiredChannels.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(connectionManager::addChannel);
    }

    private void dispatchUserList(String channel) {
        eventDispatcher.dispatch(messageParser.parseUserList(channel, userManager.getUsers(channel)));
    }

    private void handleInboundTraffic(String line) {
        IrcConfiguration configuration = configurationRef.get();
        if (configuration != null && configuration.debugTraffic()) {
            log.debug("[IRC IN ] {}", line);
            eventDispatcher.dispatch(new IrcDebugTrafficEvent(IrcTrafficDirection.INBOUND, line, Instant.now()));
        }
        if (shouldLogHistoryTraffic(line)) {
            log.debug("[IRC IN ] {}", line);
        }
    }

    private void handleOutboundTraffic(String line) {
        IrcConfiguration configuration = configurationRef.get();
        if (configuration != null && configuration.debugTraffic()) {
            log.debug("[IRC OUT] {}", line);
            eventDispatcher.dispatch(new IrcDebugTrafficEvent(IrcTrafficDirection.OUTBOUND, line, Instant.now()));
        }
        if (shouldLogHistoryTraffic(line)) {
            log.debug("[IRC OUT] {}", line);
        }
    }

    private void handleException(Exception exception) {
        log.warn("IRC client exception", exception);
        getConfiguration().ifPresent(configuration ->
                updateConnectionState(IrcConnectionStatus.ERROR, configuration, connectionState.get().currentNickname(),
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                        reconnectAttempt.get(), connectionState.get().nextReconnectDelayMillis()));
    }

    private void clearPresence() {
        pendingHistoryRequests.clear();
        activeHistoryRequests.clear();
        pendingHistoryBatches.clear();
        historicalBatchReferences.clear();
        userManager.clear();
        channelManager.knownChannels().forEach(channel -> {
            channelManager.markLeft(channel);
            dispatchUserList(channel);
        });
    }

    private void requestChannelUsers(String channel) {
        userManager.startWhoQuery(channel);
        connectionManager.sendRawLine("WHO " + channel);
    }

    private String normalizeTarget(String target) {
        if (target == null) {
            return "";
        }
        String trimmed = target.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.startsWith("@")) {
            return "@" + trimmed.substring(1).trim();
        }
        return trimmed.startsWith("#") ? IrcConfiguration.normalizeChannel(trimmed) : trimmed;
    }

    private String toWireTarget(String target) {
        return isPrivateConversation(target) ? target.substring(1) : target;
    }

    private String toPrivateConversation(String username) {
        String normalized = UserManager.stripPrefix(username);
        return normalized.isBlank() ? "" : "@" + normalized;
    }

    private boolean isPrivateConversation(String target) {
        return target.startsWith("@");
    }

    private boolean isModeratorPrefix(String prefix) {
        return "@&~%".contains(prefix);
    }

    private void onIrcTokenReady(int connectRequest, IrcConfiguration configuration, String token) {
        if (manualDisconnect.get() || connectRequestCounter.get() != connectRequest || !configuration.equals(configurationRef.get())) {
            return;
        }

        updateConnectionState(IrcConnectionStatus.CONNECTING, configuration, "",
                "Connecting to " + configuration.host() + ":" + configuration.port(), 0, 0L);
        connectionManager.connect(configuration, configuration.nickname(), token, this,
                this::handleInboundTraffic, this::handleOutboundTraffic, this::handleException);
    }

    private void handleTokenFetchFailure(int connectRequest, IrcConfiguration configuration, Throwable throwable) {
        if (connectRequestCounter.get() != connectRequest) {
            return;
        }

        Throwable cause = throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
        log.warn("Failed to obtain FAF IRC token", cause);
        updateConnectionState(IrcConnectionStatus.ERROR, configuration, "",
                cause.getMessage() == null ? "Failed to obtain FAF IRC token" : cause.getMessage(),
                reconnectAttempt.get(), 0L);
    }

    private String buildReconnectMessage(ClientConnectionEndedEvent event, long delayMillis, int attempt) {
        StringBuilder message = new StringBuilder("Connection lost, retry ").append(attempt)
                .append(" in ").append(delayMillis / 1000.0).append("s");
        event.getCause().ifPresent(cause -> message.append(" (").append(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()).append(')'));
        return message.toString();
    }

    private void updateConnectionState(IrcConnectionStatus status, IrcConfiguration configuration, String currentNick,
                                       String statusMessage, int reconnectAttempt, long nextReconnectDelayMillis) {
        IrcConnectionState state = new IrcConnectionState(
                status,
                configuration.host(),
                configuration.port(),
                configuration.nickname(),
                currentNick == null ? "" : currentNick,
                statusMessage,
                reconnectAttempt,
                nextReconnectDelayMillis,
                Instant.now()
        );
        connectionState.set(state);
        eventDispatcher.dispatch(new IrcConnectionEvent(state, state.lastUpdated()));
    }

    private void clearHistoryRequest(String target) {
        pendingHistoryRequests.remove(target);
        Set<String> referencesToRemove = ConcurrentHashMap.newKeySet();
        activeHistoryRequests.entrySet().removeIf(entry -> {
            boolean remove = entry.getValue().target().equalsIgnoreCase(target);
            if (remove) {
                referencesToRemove.add(entry.getKey());
            }
            return remove;
        });
        pendingHistoryBatches.keySet().removeIf(referencesToRemove::contains);
        historicalBatchReferences.removeIf(referencesToRemove::contains);
    }

    private void failOutstandingHistoryRequests(String message) {
        Set<CompletableFuture<IrcHistoryLoadResult>> futures = ConcurrentHashMap.newKeySet();
        pendingHistoryRequests.values().forEach(request -> futures.add(request.future()));
        activeHistoryRequests.values().forEach(request -> futures.add(request.future()));
        pendingHistoryRequests.clear();
        activeHistoryRequests.clear();
        pendingHistoryBatches.clear();
        historicalBatchReferences.clear();
        futures.forEach(future -> future.completeExceptionally(new IllegalStateException(message)));
    }

    private boolean isVisibleHistoryMessage(ClientReceiveServerMessageEvent event) {
        if (event.getParameters().size() < 2) {
            return false;
        }
        return !IrcNoiseFilter.isHiddenSystemMessage(
                event.getParameters().getFirst(),
                event.getActor().getName(),
                IrcMessageKind.CHAT,
                null,
                event.getParameters().get(1)
        );
    }

    private void finalizeHistoryReplayProgress(ServerMessageEvent event) {
        String batchReference = event.getTag("batch")
                .flatMap(MessageTag::getValue)
                .orElse(null);
        if (batchReference == null) {
            return;
        }

        PendingHistoryBatch pendingBatch = pendingHistoryBatches.get(batchReference);
        if (pendingBatch == null) {
            return;
        }

        if (pendingBatch.remainingReplayEvents().decrementAndGet() > 0) {
            return;
        }

        if (pendingHistoryBatches.remove(batchReference, pendingBatch)) {
            finalizeHistoryBatch(batchReference, pendingBatch.request(), pendingBatch.page());
        }
    }

    private boolean shouldLogHistoryTraffic(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return line.contains("CHATHISTORY")
                || line.contains(" BATCH ")
                || line.contains("batch=")
                || line.contains("draft/chathistory")
                || line.contains("HistServ");
    }

    private String abbreviateForLog(String message) {
        if (message == null) {
            return "";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() <= 160 ? singleLine : singleLine.substring(0, 160) + "...";
    }

    private enum Mode {
        RECENT,
        SINCE
    }

    private record HistoryRequestState(
            String target,
            Instant since,
            int pageLimit,
            int totalLoaded,
            CompletableFuture<IrcHistoryLoadResult> future,
            Mode mode,
            boolean reachedServerEnd
    ) {
        private HistoryRequestState withReachedServerEnd(boolean reachedServerEnd) {
            return new HistoryRequestState(target, since, pageLimit, totalLoaded, future, mode, reachedServerEnd);
        }

        private HistoryRequestState withTotalLoaded(int totalLoaded) {
            return new HistoryRequestState(target, since, pageLimit, totalLoaded, future, mode, reachedServerEnd);
        }
    }

    private record HistoryPage(int replayMessageCount, int visibleMessageCount, String earliestSelector, Instant earliestTimestamp) {
    }

    private record PendingHistoryBatch(
            HistoryRequestState request,
            HistoryPage page,
            AtomicInteger remainingReplayEvents
    ) {
    }
}
