package com.faforever.moderatorclient.irc;

import java.time.Instant;

public record IrcHistoryLoadResult(
        String target,
        int loadedCount,
        Instant requestedSince,
        boolean reachedRequestedStart,
        boolean reachedServerEnd
) {
}
