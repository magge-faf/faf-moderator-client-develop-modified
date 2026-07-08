package com.faforever.moderatorclient.api;

import com.faforever.commons.lobby.FafLobbyClient;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class LobbyModerationServiceTest {

    @Test
    void kickFromGameAndClientRunsBothActionsInOrder() {
        LobbyModerationService service = spy(new LobbyModerationService(
                mock(FafLobbyClient.class),
                mock(LobbyOAuthService.class),
                mock(LobbyUidService.class)));
        doNothing().when(service).kickFromGame(42);
        doNothing().when(service).kickFromClient(42);

        service.kickFromGameAndClient(42);

        InOrder inOrder = inOrder(service);
        inOrder.verify(service).kickFromGame(42);
        inOrder.verify(service).kickFromClient(42);
    }

    @Test
    void kickFromGameAndClientStillAttemptsClientKickAfterGameFailure() {
        LobbyModerationService service = spy(new LobbyModerationService(
                mock(FafLobbyClient.class),
                mock(LobbyOAuthService.class),
                mock(LobbyUidService.class)));
        RuntimeException gameFailure = new RuntimeException("game failed");
        RuntimeException clientFailure = new RuntimeException("client failed");
        doThrow(gameFailure).when(service).kickFromGame(7);
        doThrow(clientFailure).when(service).kickFromClient(7);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.kickFromGameAndClient(7));

        assertSame(gameFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(clientFailure, thrown.getSuppressed()[0]);
    }
}
