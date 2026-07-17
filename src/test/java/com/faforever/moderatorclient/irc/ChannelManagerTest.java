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
                "msg-1",
                IrcMessageKind.CHAT,
                false,
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

    @Test
    void sortsHistoryChronologicallyAndDeduplicatesByMessageId() {
        ChannelManager channelManager = new ChannelManager();
        UserManager userManager = new UserManager();

        channelManager.addMessage(new IrcChannelMessageEvent(
                "#Aeolus",
                "LaterUser",
                "later",
                "msg-2",
                IrcMessageKind.CHAT,
                false,
                true,
                Instant.parse("2026-06-21T10:16:30Z")
        ), false);
        channelManager.addMessage(new IrcChannelMessageEvent(
                "#Aeolus",
                "EarlierUser",
                "earlier",
                "msg-1",
                IrcMessageKind.CHAT,
                false,
                true,
                Instant.parse("2026-06-21T10:15:30Z")
        ), false);
        channelManager.addMessage(new IrcChannelMessageEvent(
                "#Aeolus",
                "DuplicateUser",
                "duplicate payload should be ignored",
                "msg-1",
                IrcMessageKind.CHAT,
                false,
                true,
                Instant.parse("2026-06-21T10:17:30Z")
        ), false);

        IrcChannelSnapshot snapshot = channelManager.snapshot("#Aeolus", userManager).orElseThrow();
        assertEquals(2, snapshot.history().size());
        assertEquals("msg-1", snapshot.history().get(0).messageId());
        assertEquals("earlier", snapshot.history().get(0).text());
        assertEquals("msg-2", snapshot.history().get(1).messageId());
        assertTrue(snapshot.history().stream().allMatch(IrcMessageEntry::historical));
    }

    @Test
    void nonChatNotificationsDoNotIncreaseUnreadCount() {
        ChannelManager channelManager = new ChannelManager();
        UserManager userManager = new UserManager();

        channelManager.addNotification("#Aeolus", "UserA", "UserA joined", IrcChannelNotificationType.JOIN,
                Instant.parse("2026-06-21T10:15:30Z"), true);
        channelManager.addNotification("#Aeolus", "UserA", "UserA left", IrcChannelNotificationType.PART,
                Instant.parse("2026-06-21T10:16:30Z"), true);
        channelManager.addNotification("#Aeolus", "UserB", "UserB quit", IrcChannelNotificationType.QUIT,
                Instant.parse("2026-06-21T10:17:30Z"), true);
        channelManager.addNotification("#Aeolus", "UserC", "UserC is now UserD", IrcChannelNotificationType.NICK_CHANGE,
                Instant.parse("2026-06-21T10:18:30Z"), true);
        channelManager.addNotification("#Aeolus", "Moderator", "Topic: keep chat civil", IrcChannelNotificationType.TOPIC,
                Instant.parse("2026-06-21T10:19:30Z"), true);

        IrcChannelSnapshot snapshot = channelManager.snapshot("#Aeolus", userManager).orElseThrow();
        assertEquals(0, snapshot.unreadCount());
        assertEquals(5, snapshot.history().size());
    }

    @Test
    void historyServerNoiseDoesNotIncreaseUnreadCount() {
        ChannelManager channelManager = new ChannelManager();
        UserManager userManager = new UserManager();

        channelManager.addMessage(new IrcChannelMessageEvent(
                "#Aeolus",
                "HistServ",
                "Heidhrekr [account: Heidhrekr] joined the channel",
                "msg-join",
                IrcMessageKind.CHAT,
                false,
                true,
                Instant.parse("2026-06-21T10:15:30Z")
        ), true);
        channelManager.addMessage(new IrcChannelMessageEvent(
                "#Aeolus",
                "HistServ",
                "dvoyka quit (connection closed)",
                "msg-quit",
                IrcMessageKind.CHAT,
                false,
                true,
                Instant.parse("2026-06-21T10:16:30Z")
        ), true);
        channelManager.addMessage(new IrcChannelMessageEvent(
                "#Aeolus",
                "maggie",
                "maggie joined #Aeolus",
                "msg-user-join",
                IrcMessageKind.CHAT,
                false,
                true,
                Instant.parse("2026-06-21T10:17:30Z")
        ), true);

        IrcChannelSnapshot snapshot = channelManager.snapshot("#Aeolus", userManager).orElseThrow();
        assertEquals(0, snapshot.unreadCount());
        assertEquals(3, snapshot.history().size());
    }
}
