package com.faforever.moderatorclient.irc;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ChannelManager {
    private static final int MAX_HISTORY = 500;

    private final Map<String, ChannelState> channels = new ConcurrentHashMap<>();

    void ensureChannels(Set<String> channelNames) {
        if (channelNames == null) {
            return;
        }
        channelNames.forEach(this::ensureChannel);
    }

    void ensureChannel(String channel) {
        ensureChannelState(channel);
    }

    void markJoined(String channel) {
        ensureChannelState(channel).joined = true;
    }

    void markLeft(String channel) {
        ChannelState state = ensureChannelState(channel);
        state.joined = false;
        state.unreadCount = 0;
    }

    void updateTopic(String channel, String topic) {
        ensureChannelState(channel).topic = topic == null ? "" : topic;
    }

    void addMessage(IrcChannelMessageEvent event, boolean incrementUnread) {
        ChannelState state = ensureChannelState(event.channel());
        state.append(new IrcMessageEntry(
                event.channel(),
                event.author(),
                event.message(),
                event.kind(),
                null,
                event.timestamp(),
                event.ownMessage()
        ));
        if (incrementUnread) {
            state.unreadCount++;
        }
    }

    void addNotification(String channel, String sender, String message, IrcChannelNotificationType type, Instant timestamp, boolean incrementUnread) {
        ChannelState state = ensureChannelState(channel);
        state.append(new IrcMessageEntry(
                channel,
                sender,
                message,
                type == IrcChannelNotificationType.ERROR ? IrcMessageKind.NOTICE : IrcMessageKind.SYSTEM,
                type,
                timestamp,
                false
        ));
        if (incrementUnread) {
            state.unreadCount++;
        }
    }

    void markRead(String channel) {
        Optional.ofNullable(channels.get(channel)).ifPresent(state -> state.unreadCount = 0);
    }

    Optional<IrcChannelSnapshot> snapshot(String channel, UserManager userManager) {
        ChannelState state = channels.get(channel);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(state.snapshot(userManager.getUsers(channel)));
    }

    List<IrcChannelSnapshot> snapshots(UserManager userManager) {
        return channels.values()
                .stream()
                .sorted(Comparator.comparing(ChannelState::name, String.CASE_INSENSITIVE_ORDER))
                .map(state -> state.snapshot(userManager.getUsers(state.name)))
                .toList();
    }

    Set<String> knownChannels() {
        return new LinkedHashSet<>(channels.keySet());
    }

    void removeChannel(String channel) {
        channels.remove(channel);
    }

    void clear() {
        channels.clear();
    }

    private ChannelState ensureChannelState(String channel) {
        return channels.computeIfAbsent(channel, ChannelState::new);
    }

    private static final class ChannelState {
        private final String name;
        private String topic = "";
        private boolean joined;
        private int unreadCount;
        private final Deque<IrcMessageEntry> history = new ArrayDeque<>();

        private ChannelState(String name) {
            this.name = name;
        }

        private String name() {
            return name;
        }

        private void append(IrcMessageEntry entry) {
            history.addLast(entry);
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }

        private IrcChannelSnapshot snapshot(List<String> users) {
            return new IrcChannelSnapshot(
                    name,
                    topic,
                    joined,
                    unreadCount,
                    List.copyOf(users),
                    List.copyOf(new ArrayList<>(history))
            );
        }
    }
}
