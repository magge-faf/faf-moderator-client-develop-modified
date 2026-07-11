package com.faforever.moderatorclient.irc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ChannelManager {
    private static final int MAX_HISTORY = 50_000;
    private static final Comparator<IrcMessageEntry> HISTORY_ORDER = Comparator
            .comparing(IrcMessageEntry::timestamp)
            .thenComparing(entry -> entry.messageId() == null ? "" : entry.messageId())
            .thenComparing(entry -> entry.sender() == null ? "" : entry.sender())
            .thenComparing(entry -> entry.text() == null ? "" : entry.text());

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
        ensureChannelState(channel).setJoined(true);
    }

    void markLeft(String channel) {
        ChannelState state = ensureChannelState(channel);
        state.setJoined(false);
        state.setUnreadCount(0);
    }

    void updateTopic(String channel, String topic) {
        ensureChannelState(channel).setTopic(topic == null ? "" : topic);
    }

    void addMessage(IrcChannelMessageEvent event, boolean incrementUnread) {
        ChannelState state = ensureChannelState(event.channel());
        IrcMessageEntry entry = new IrcMessageEntry(
                event.channel(),
                event.author(),
                event.message(),
                event.messageId(),
                event.kind(),
                null,
                event.timestamp(),
                event.ownMessage(),
                event.historical()
        );
        boolean added = state.append(entry);
        if (added && incrementUnread && IrcNoiseFilter.countsAsUnread(entry)) {
            state.incrementUnreadCount();
        }
    }

    void addNotification(String channel, String sender, String message, IrcChannelNotificationType type, Instant timestamp, boolean incrementUnread) {
        ChannelState state = ensureChannelState(channel);
        IrcMessageEntry entry = new IrcMessageEntry(
                channel,
                sender,
                message,
                null,
                type == IrcChannelNotificationType.ERROR ? IrcMessageKind.NOTICE : IrcMessageKind.SYSTEM,
                type,
                timestamp,
                false,
                false
        );
        boolean added = state.append(entry);
        if (added && incrementUnread && IrcNoiseFilter.countsAsUnread(entry)) {
            state.incrementUnreadCount();
        }
    }

    void markRead(String channel) {
        Optional.ofNullable(channels.get(channel)).ifPresent(state -> state.setUnreadCount(0));
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
                .map(state -> state.snapshot(userManager.getUsers(state.name())))
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
        private final List<IrcMessageEntry> history = new ArrayList<>();

        private ChannelState(String name) {
            this.name = name;
        }

        private synchronized String name() {
            return name;
        }

        private synchronized void setTopic(String topic) {
            this.topic = topic;
        }

        private synchronized void setJoined(boolean joined) {
            this.joined = joined;
        }

        private synchronized void setUnreadCount(int count) {
            this.unreadCount = count;
        }

        private synchronized void incrementUnreadCount() {
            this.unreadCount++;
        }

        private synchronized boolean append(IrcMessageEntry entry) {
            if (contains(entry)) {
                return false;
            }
            history.add(entry);
            history.sort(HISTORY_ORDER);
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
            return true;
        }

        private boolean contains(IrcMessageEntry entry) {
            if (entry.messageId() != null && !entry.messageId().isBlank()) {
                return history.stream().anyMatch(existing -> entry.messageId().equals(existing.messageId()));
            }
            return history.stream().anyMatch(existing ->
                    existing.timestamp().equals(entry.timestamp())
                            && equalsIgnoreNull(existing.sender(), entry.sender())
                            && equalsIgnoreNull(existing.text(), entry.text())
                            && existing.kind() == entry.kind());
        }

        private boolean equalsIgnoreNull(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }

        private synchronized IrcChannelSnapshot snapshot(List<String> users) {
            return new IrcChannelSnapshot(
                    name,
                    topic,
                    joined,
                    unreadCount,
                    List.copyOf(users),
                    List.copyOf(history)
            );
        }
    }
}
