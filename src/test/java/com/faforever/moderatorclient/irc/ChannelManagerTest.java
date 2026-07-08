package com.faforever.moderatorclient.irc;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelManagerTest {
    @Test
    void tracksUnreadMessagesTopicAndReadState() {
        ChannelManager channelManager = new ChannelManager();
        UserManager userManager = new UserManager();

        channelManager.markJoined("#Aeolus");
        channelManager.updateTopic("#Aeolus", "FAF general chat");
        channelManager.addMessage(new IrcChannelMessageEvent(
                "#Aeolus",
                "Moderator",
                "Hello world",
                IrcMessageKind.CHAT,
                false,
                Instant.parse("2026-06-21T10:15:30Z")
        ), true);

        IrcChannelSnapshot snapshot = channelManager.snapshot("#Aeolus", userManager).orElseThrow();
        assertTrue(snapshot.joined());
        assertEquals("FAF general chat", snapshot.topic());
        assertEquals(1, snapshot.unreadCount());
        assertEquals(1, snapshot.history().size());

        channelManager.markRead("#Aeolus");

        IrcChannelSnapshot afterRead = channelManager.snapshot("#Aeolus", userManager).orElseThrow();
        assertEquals(0, afterRead.unreadCount());
    }
}
