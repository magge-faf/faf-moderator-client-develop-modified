package com.faforever.moderatorclient.irc;

import java.time.Instant;

public sealed interface IrcEvent permits IrcChannelMessageEvent, IrcChannelNotificationEvent, IrcConnectionEvent, IrcDebugTrafficEvent, IrcTopicEvent, IrcUserListEvent {
    Instant timestamp();
}
