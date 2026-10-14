package com.faforever.moderatorclient.api.domain;

import com.faforever.commons.api.dto.BanInfo;
import com.faforever.commons.api.dto.Player;
import com.faforever.moderatorclient.api.BrowserReloginService;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.FafUserCommunicationService;
import com.faforever.moderatorclient.api.UserServiceFailure;
import com.faforever.moderatorclient.api.event.RefreshTokenRevocationEvent;
import com.faforever.moderatorclient.api.event.RefreshTokenRevocationEvent.Status;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import com.faforever.moderatorclient.mapstruct.BanInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BanServiceTest {

    private static final UserServiceFailure CHALLENGE = new UserServiceFailure("HTTP 403, blocked by Cloudflare (cf-mitigated: challenge)", true);

    private final FafApiCommunicationService fafApi = mock();
    private final FafUserCommunicationService fafUser = mock();
    private final BrowserReloginService browserReloginService = mock();
    private final ApplicationEventPublisher applicationEventPublisher = mock();
    private BanService instance;

    @BeforeEach
    void setUp() {
        instance = new BanService(mock(BanInfoMapper.class), fafApi, fafUser, new EnvironmentProperties(),
                browserReloginService, applicationEventPublisher);
    }

    @Test
    void doesNotRevokeOrNotifyWhenBanWriteFails() {
        when(fafApi.patch(any(), any(BanInfo.class))).thenThrow(new IllegalStateException("API down"));

        assertThrows(IllegalStateException.class, () -> instance.updateBan(banFor("42")));

        verify(fafUser, never()).tryPost(any(), any());
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void revokesWithoutReloginWhenRequestPasses() {
        when(fafUser.tryPost(eq("/oauth2/revokeTokens"), any())).thenReturn(null);

        instance.updateBan(banFor("42"));

        verify(browserReloginService, never()).relogin();
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
        verify(fafApi).patch(any(), any(BanInfo.class));
    }

    @Test
    void reloginsAndRetriesWhenCloudflareChallengesTheRevoke() {
        when(fafUser.tryPost(eq("/oauth2/revokeTokens"), any())).thenReturn(CHALLENGE, (UserServiceFailure) null);
        when(browserReloginService.relogin()).thenReturn(CompletableFuture.completedFuture(null));

        instance.updateBan(banFor("42"));

        verify(fafUser, times(2)).tryPost(eq("/oauth2/revokeTokens"), any());
        verify(applicationEventPublisher).publishEvent(new RefreshTokenRevocationEvent("42", Status.RELOGIN_REQUIRED, CHALLENGE.reason()));
        verify(applicationEventPublisher).publishEvent(new RefreshTokenRevocationEvent("42", Status.REVOKED_AFTER_RELOGIN, null));
        verify(fafApi).patch(any(), any(BanInfo.class));
    }

    @Test
    void reportsFailureWhenReloginDoesNotComplete() {
        when(fafUser.tryPost(eq("/oauth2/revokeTokens"), any())).thenReturn(CHALLENGE);
        when(browserReloginService.relogin()).thenReturn(CompletableFuture.failedFuture(new TimeoutException()));

        instance.updateBan(banFor("42"));

        verify(fafUser, times(1)).tryPost(eq("/oauth2/revokeTokens"), any());
        verify(applicationEventPublisher).publishEvent(new RefreshTokenRevocationEvent("42", Status.FAILED, "browser login did not complete: timed out"));
        verify(fafApi).patch(any(), any(BanInfo.class));
    }

    @Test
    void doesNotAskForReloginWhenFailureIsNotACloudflareChallenge() {
        UserServiceFailure forbidden = new UserServiceFailure("HTTP 403 Forbidden", false);
        when(fafUser.tryPost(eq("/oauth2/revokeTokens"), any())).thenReturn(forbidden);

        instance.updateBan(banFor("42"));

        verify(browserReloginService, never()).relogin();
        verify(applicationEventPublisher).publishEvent(new RefreshTokenRevocationEvent("42", Status.FAILED, "HTTP 403 Forbidden"));
    }

    private static BanInfo banFor(String playerId) {
        Player player = new Player();
        player.setId(playerId);
        BanInfo banInfo = new BanInfo();
        banInfo.setId("1");
        banInfo.setPlayer(player);
        return banInfo;
    }
}
