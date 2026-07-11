package com.faforever.moderatorclient.irc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrcMentionMatcherTest {

    @Test
    void detectsCaseInsensitiveNicknameMention() {
        assertTrue(IrcMentionMatcher.containsMention("Can MAGGE take a look?", "magge"));
    }

    @Test
    void doesNotMatchNicknameInsideAnotherWord() {
        assertFalse(IrcMentionMatcher.containsMention("demagged replay", "magge"));
    }

    @Test
    void matchesNicknameWithPunctuationBoundary() {
        assertTrue(IrcMentionMatcher.containsMention("magge, please check this user", "magge"));
    }

    @Test
    void systemJoinNoiseShouldNotBeTreatedAsRealMention() {
        IrcChannelMessageEvent event = new IrcChannelMessageEvent(
                "#moderators",
                "HistServ",
                "magge [account: magge] joined the channel",
                "msg-1",
                IrcMessageKind.CHAT,
                false,
                false,
                java.time.Instant.parse("2026-07-11T12:00:00Z")
        );

        assertTrue(IrcMentionMatcher.containsMention(event.message(), "magge"));
        assertTrue(IrcNoiseFilter.isHiddenSystemMessage(event));
    }
}
