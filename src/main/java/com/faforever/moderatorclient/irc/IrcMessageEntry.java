package com.faforever.moderatorclient.irc;

import java.time.Instant;

public record IrcMessageEntry(
        String channel,
        String sender,
        String text,
        IrcMessageKind kind,
        IrcChannelNotificationType notificationType,
        Instant timestamp,
        boolean ownMessage
) {
}
