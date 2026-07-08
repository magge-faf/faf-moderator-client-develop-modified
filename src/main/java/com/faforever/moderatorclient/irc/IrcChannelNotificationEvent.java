package com.faforever.moderatorclient.irc;

import java.time.Instant;

public record IrcChannelNotificationEvent(
        String channel,
        IrcChannelNotificationType type,
        String actor,
        String target,
        String message,
        Instant timestamp
) implements IrcEvent {
}
