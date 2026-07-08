package com.faforever.moderatorclient.irc;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrcConfigurationTest {
    @Test
    void usesDefaultsAndNormalizesChannels() {
        IrcConfiguration configuration = new IrcConfiguration(
                "",
                0,
                " Moderator ",
                true,
                Set.of("Aeolus", "#moderators")
        );

        assertEquals(IrcConfiguration.DEFAULT_HOST, configuration.host());
        assertEquals(IrcConfiguration.DEFAULT_PORT, configuration.port());
        assertEquals("Moderator", configuration.nickname());
        assertEquals(Set.of("#Aeolus", "#moderators"), configuration.autoJoinChannels());
    }
}
