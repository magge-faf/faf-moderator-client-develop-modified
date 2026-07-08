package com.faforever.moderatorclient.irc;

import java.time.Instant;

public record IrcDebugTrafficEvent(
        IrcTrafficDirection direction,
        String line,
        Instant timestamp
) implements IrcEvent {
}
