package com.faforever.moderatorclient.irc;

import java.time.Instant;

public record IrcTopicEvent(
        String channel,
        String topic,
        String setBy,
        Instant timestamp
) implements IrcEvent {
}
