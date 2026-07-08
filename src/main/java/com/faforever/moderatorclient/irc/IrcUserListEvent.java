package com.faforever.moderatorclient.irc;

import java.time.Instant;
import java.util.List;

public record IrcUserListEvent(
        String channel,
        List<String> users,
        Instant timestamp
) implements IrcEvent {
}
