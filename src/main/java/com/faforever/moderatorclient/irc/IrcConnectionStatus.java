package com.faforever.moderatorclient.irc;

public enum IrcConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    NICKNAME_CONFLICT,
    ERROR
}
