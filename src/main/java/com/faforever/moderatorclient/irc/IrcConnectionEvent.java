package com.faforever.moderatorclient.irc;

import java.time.Instant;

public record IrcConnectionEvent(
        IrcConnectionState state,
        Instant timestamp
) implements IrcEvent {
}
