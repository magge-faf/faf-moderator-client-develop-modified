package com.faforever.moderatorclient.irc;

import java.time.Instant;

public record IrcChannelMessageEvent(
        String channel,
        String author,
        String message,
        String messageId,
        IrcMessageKind kind,
        boolean ownMessage,
        boolean historical,
        Instant timestamp
) implements IrcEvent {
}
