package com.faforever.moderatorclient.irc;

import java.time.Instant;

public record IrcConnectionState(
        IrcConnectionStatus status,
        String host,
        int port,
        String configuredNickname,
        String currentNickname,
        String statusMessage,
        int reconnectAttempt,
        long nextReconnectDelayMillis,
        Instant lastUpdated
) {
    public static IrcConnectionState disconnected() {
        return new IrcConnectionState(
                IrcConnectionStatus.DISCONNECTED,
                IrcConfiguration.DEFAULT_HOST,
                IrcConfiguration.DEFAULT_PORT,
                "",
                "",
                "Disconnected",
                0,
                0L,
                Instant.now()
        );
    }
}
