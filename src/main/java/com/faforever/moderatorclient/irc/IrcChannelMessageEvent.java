package com.faforever.moderatorclient.irc;

import java.time.Instant;

public record IrcChannelMessageEvent(
        String channel,
        String author,
        String message,
        IrcMessageKind kind,
        boolean ownMessage,
        Instant timestamp
) implements IrcEvent {
}
