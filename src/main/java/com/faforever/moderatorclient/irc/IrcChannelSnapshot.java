package com.faforever.moderatorclient.irc;

import java.util.List;

public record IrcChannelSnapshot(
        String name,
        String topic,
        boolean joined,
        int unreadCount,
        List<String> users,
        List<IrcMessageEntry> history
) {
}
