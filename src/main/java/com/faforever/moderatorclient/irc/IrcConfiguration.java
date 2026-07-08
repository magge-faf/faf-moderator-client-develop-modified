package com.faforever.moderatorclient.irc;

import java.util.LinkedHashSet;
import java.util.Set;

public record IrcConfiguration(
        String host,
        int port,
        String nickname,
        boolean debugTraffic,
        Set<String> autoJoinChannels
) {
    public static final String DEFAULT_HOST = "chat.faforever.com";
    public static final int DEFAULT_PORT = 443;
    public static final String DEFAULT_CHANNEL = "#aeolus";
    public static final Set<String> DEFAULT_AUTO_JOIN_CHANNELS = Set.of("#aeolus", "#moderators");

    public IrcConfiguration {
        host = normalizeHost(host);
        port = port <= 0 ? DEFAULT_PORT : port;
        nickname = normalizeText(nickname);
        debugTraffic = debugTraffic;
        autoJoinChannels = normalizeChannels(autoJoinChannels);
    }

    public static IrcConfiguration defaultConfiguration(String nickname) {
        return new IrcConfiguration(
                DEFAULT_HOST,
                DEFAULT_PORT,
                nickname,
                false,
                DEFAULT_AUTO_JOIN_CHANNELS
        );
    }

    private static String normalizeHost(String value) {
        String host = normalizeText(value);
        return host.isBlank() ? DEFAULT_HOST : host;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static Set<String> normalizeChannels(Set<String> values) {
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .map(IrcConfiguration::normalizeChannel)
                    .filter(channel -> !channel.isBlank())
                    .forEach(channels::add);
        }
        if (channels.isEmpty()) {
            channels.addAll(DEFAULT_AUTO_JOIN_CHANNELS);
        }
        return Set.copyOf(channels);
    }

    public static String normalizeChannel(String channel) {
        String normalized = normalizeText(channel);
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.startsWith("#") ? normalized : "#" + normalized;
    }
}
