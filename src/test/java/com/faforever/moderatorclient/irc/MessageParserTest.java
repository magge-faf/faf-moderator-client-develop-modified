package com.faforever.moderatorclient.irc;

import org.junit.jupiter.api.Test;
import org.kitteh.irc.client.library.event.client.ClientReceiveNumericEvent;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class MessageParserTest {
    @Test
    void parsesWhoUserAndCompletionNumerics() {
        MessageParser parser = new MessageParser();
        ClientReceiveNumericEvent whoEvent = Mockito.mock(ClientReceiveNumericEvent.class);
        when(whoEvent.getNumeric()).thenReturn(352);
        when(whoEvent.getParameters()).thenReturn(List.of("me", "#aeolus", "user", "host", "server", "Moderator", "H", "0 real"));

        MessageParser.WhoUser whoUser = parser.parseWhoUser(whoEvent).orElseThrow();
        assertEquals("#aeolus", whoUser.channel());
        assertEquals("Moderator", whoUser.nick());

        ClientReceiveNumericEvent completeEvent = Mockito.mock(ClientReceiveNumericEvent.class);
        when(completeEvent.getNumeric()).thenReturn(315);
        when(completeEvent.getParameters()).thenReturn(List.of("me", "#aeolus", "End of WHO list"));

        assertEquals("#aeolus", parser.parseWhoComplete(completeEvent).orElseThrow());
    }

    @Test
    void ignoresTooShortWhoNumeric() {
        MessageParser parser = new MessageParser();
        ClientReceiveNumericEvent whoEvent = Mockito.mock(ClientReceiveNumericEvent.class);
        when(whoEvent.getNumeric()).thenReturn(352);
        when(whoEvent.getParameters()).thenReturn(List.of("me", "#aeolus"));

        assertTrue(parser.parseWhoUser(whoEvent).isEmpty());
    }
}
