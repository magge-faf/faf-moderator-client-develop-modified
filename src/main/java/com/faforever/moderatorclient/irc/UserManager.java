package com.faforever.moderatorclient.irc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

final class UserManager {
    private final Map<String, NavigableSet<String>> usersByChannel = new ConcurrentHashMap<>();
    private final Map<String, NavigableSet<String>> pendingWhoUsersByChannel = new ConcurrentHashMap<>();

    void ensureChannel(String channel) {
        usersByChannel.computeIfAbsent(channel, ignored -> new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER));
    }

    void replaceUsers(String channel, Collection<String> users) {
        NavigableSet<String> normalized = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);
        if (users != null) {
            users.stream()
                    .map(UserManager::normalizeNick)
                    .filter(nick -> !nick.isBlank())
                    .forEach(normalized::add);
        }
        pendingWhoUsersByChannel.remove(channel);
        usersByChannel.put(channel, normalized);
    }

    void addUser(String channel, String nick) {
        ensureChannel(channel);
        String normalized = normalizeNick(nick);
        if (!normalized.isBlank()) {
            usersByChannel.get(channel).add(normalized);
            pendingWhoUsersByChannel.computeIfPresent(channel, (ignored, users) -> {
                users.add(normalized);
                return users;
            });
        }
    }

    void removeUser(String channel, String nick) {
        String normalized = normalizeNick(nick);
        if (normalized.isBlank()) {
            return;
        }
        NavigableSet<String> users = usersByChannel.get(channel);
        if (users != null) {
            users.remove(normalized);
        }
        NavigableSet<String> pendingUsers = pendingWhoUsersByChannel.get(channel);
        if (pendingUsers != null) {
            pendingUsers.remove(normalized);
        }
    }

    void renameUser(String oldNick, String newNick) {
        String normalizedOld = normalizeNick(oldNick);
        String normalizedNew = normalizeNick(newNick);
        if (normalizedOld.isBlank() || normalizedNew.isBlank()) {
            return;
        }
        usersByChannel.values().forEach(users -> {
            if (users.remove(normalizedOld)) {
                users.add(normalizedNew);
            }
        });
        pendingWhoUsersByChannel.values().forEach(users -> {
            if (users.remove(normalizedOld)) {
                users.add(normalizedNew);
            }
        });
    }

    void promoteUserPrefix(String channel, String nick, String prefix) {
        String normalizedNick = normalizeNick(nick);
        String prefixedNick = normalizeNick(prefix + nick);
        if (normalizedNick.isBlank() || prefixedNick.isBlank()) {
            return;
        }
        NavigableSet<String> users = usersByChannel.get(channel);
        if (users != null) {
            users.removeIf(user -> stripPrefix(user).equalsIgnoreCase(normalizedNick));
            users.add(prefixedNick);
        }
        NavigableSet<String> pendingUsers = pendingWhoUsersByChannel.get(channel);
        if (pendingUsers != null) {
            pendingUsers.removeIf(user -> stripPrefix(user).equalsIgnoreCase(normalizedNick));
            pendingUsers.add(prefixedNick);
        }
    }

    void demoteUserPrefix(String channel, String nick) {
        String normalizedNick = normalizeNick(nick);
        if (normalizedNick.isBlank()) {
            return;
        }
        NavigableSet<String> users = usersByChannel.get(channel);
        if (users != null) {
            users.removeIf(user -> stripPrefix(user).equalsIgnoreCase(normalizedNick));
            users.add(normalizedNick);
        }
        NavigableSet<String> pendingUsers = pendingWhoUsersByChannel.get(channel);
        if (pendingUsers != null) {
            pendingUsers.removeIf(user -> stripPrefix(user).equalsIgnoreCase(normalizedNick));
            pendingUsers.add(normalizedNick);
        }
    }

    void removeUserEverywhere(String nick) {
        String normalized = normalizeNick(nick);
        if (normalized.isBlank()) {
            return;
        }
        usersByChannel.values().forEach(users -> users.remove(normalized));
        pendingWhoUsersByChannel.values().forEach(users -> users.remove(normalized));
    }

    void startWhoQuery(String channel) {
        ensureChannel(channel);
        pendingWhoUsersByChannel.put(channel, new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER));
    }

    void addWhoUser(String channel, String nick) {
        String normalized = normalizeNick(nick);
        if (normalized.isBlank()) {
            return;
        }
        pendingWhoUsersByChannel.computeIfAbsent(channel, ignored -> new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER)).add(normalized);
    }

    void completeWhoQuery(String channel) {
        NavigableSet<String> pendingUsers = pendingWhoUsersByChannel.remove(channel);
        if (pendingUsers != null) {
            usersByChannel.put(channel, new ConcurrentSkipListSet<>(pendingUsers));
        }
    }

    List<String> channelsContaining(String nick) {
        String normalized = normalizeNick(nick);
        if (normalized.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> channels = new LinkedHashSet<>();
        usersByChannel.forEach((channel, users) -> {
            if (users.stream().anyMatch(user -> stripPrefix(user).equalsIgnoreCase(normalized))) {
                channels.add(channel);
            }
        });
        return List.copyOf(channels);
    }

    List<String> getUsers(String channel) {
        NavigableSet<String> users = usersByChannel.get(channel);
        if (users == null) {
            return List.of();
        }
        return new ArrayList<>(users);
    }

    void clear() {
        usersByChannel.clear();
        pendingWhoUsersByChannel.clear();
    }

    private static String normalizeNick(String nick) {
        return nick == null ? "" : nick.trim();
    }

    public static String stripPrefix(String nick) {
        String normalized = normalizeNick(nick);
        if (!normalized.isBlank() && "@&~%+".indexOf(normalized.charAt(0)) >= 0) {
            return normalized.substring(1);
        }
        return normalized;
    }
}
