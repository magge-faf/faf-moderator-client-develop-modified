package com.faforever.moderatorclient.api.domain;

import com.faforever.commons.api.dto.BanInfo;
import com.faforever.commons.api.elide.ElideNavigator;
import com.faforever.commons.api.elide.ElideNavigatorOnCollection;
import com.faforever.commons.api.elide.ElideNavigatorOnId;
import com.faforever.moderatorclient.api.BrowserReloginService;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.FafUserCommunicationService;
import com.faforever.moderatorclient.api.UserServiceFailure;
import com.faforever.moderatorclient.api.dto.hydra.RevokeRefreshTokenRequest;
import com.faforever.moderatorclient.api.event.RefreshTokenRevocationEvent;
import com.faforever.moderatorclient.api.event.RefreshTokenRevocationEvent.Status;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import com.faforever.moderatorclient.mapstruct.BanInfoMapper;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class BanService {
    private static final String REVOKE_ENDPOINT = "/oauth2/revokeTokens";

    private final BanInfoMapper banInfoMapper;
    private final FafApiCommunicationService fafApi;
    private final FafUserCommunicationService fafUser;
    private final EnvironmentProperties environmentProperties;
    private final BrowserReloginService browserReloginService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void patchBanInfo(@NotNull BanInfoFX banInfoFX) {
        BanInfo banInfo = banInfoMapper.map(banInfoFX);
        log.debug("Patching BanInfo of id: {}", banInfo.getId());
        tryRevokeTokens(banInfo.getPlayer().getId());
        banInfo.setAuthor(null);
        banInfo.setPlayer(null);
        fafApi.patch(ElideNavigator.of(BanInfo.class).id(banInfo.getId()), banInfo);
    }

    public String createBan(@NotNull BanInfoFX banInfoFX) {
        BanInfo banInfo = banInfoMapper.map(banInfoFX);
        tryRevokeTokens(banInfo.getPlayer().getId());
        return fafApi.post(ElideNavigator.of(BanInfo.class).collection(), banInfo).getId();
    }

    public String revokeThenCreateBan(@NotNull BanInfoFX banInfoFX) {
        BanInfo mapped = banInfoMapper.map(banInfoFX);

        // Create the new ban first to ensure atomicity
        mapped.setId(null);
        String newBanId = fafApi.post(ElideNavigator.of(BanInfo.class).collection(), mapped).getId();

        // Only after successful creation, revoke the old ban
        try {
            BanInfo revokeDto = new BanInfo();
            revokeDto.setId(banInfoFX.getId());
            revokeDto.setPlayer(mapped.getPlayer());
            revokeDto.setRevokeReason("Ban updated by moderator");
            revokeDto.setRevokeTime(OffsetDateTime.now(ZoneOffset.UTC));
            updateBan(revokeDto);
        } catch (Exception e) {
            log.error("Failed to revoke old ban {} after creating new ban {}", banInfoFX.getId(), newBanId, e);
            throw e;
        }

        return newBanId;
    }

    public void updateBan(BanInfo banInfoUpdate) {
        log.debug("Update for ban id: {}", banInfoUpdate.getId());
        ElideNavigatorOnId<BanInfo> navigator = ElideNavigator.of(BanInfo.class)
                .id(banInfoUpdate.getId());

        tryRevokeTokens(banInfoUpdate.getPlayer().getId());
        fafApi.patch(navigator, banInfoUpdate);
    }

    private void tryRevokeTokens(String playerId) {
        // Best-effort session cleanup: revoking all refresh tokens forces the banned player to sign in again on FAF
        // clients once their current access token expires. A failure here must not block the ban itself.
        // Cloudflare only lets the request through with a recent X-HMAC, so mint a fresh one first.
        UserServiceFailure hmacFailure = fafUser.tryRefreshHmac();
        if (hmacFailure != null) {
            log.warn("Could not refresh HMAC before revoking refresh tokens ({}); trying with the HMAC from login", hmacFailure);
        }
        UserServiceFailure failure = fafUser.tryPost(REVOKE_ENDPOINT, RevokeRefreshTokenRequest.allClientsOf(playerId));
        if (failure == null) {
            log.info("Revoked all refresh tokens for player {}", playerId);
        } else if (!failure.cloudflareChallenge()) {
            notRevoked(playerId, failure.reason());
        } else {
            // Only a browser login mints a new HMAC. Run it in the background so the ban itself is not held up.
            log.warn("Revoking refresh tokens for player {} was blocked by Cloudflare ({}); asking the moderator to log in again", playerId, failure);
            publishRevocation(playerId, Status.RELOGIN_REQUIRED, failure.reason());
            browserReloginService.relogin()
                    .thenRun(() -> {
                        UserServiceFailure retryFailure = fafUser.tryPost(REVOKE_ENDPOINT, RevokeRefreshTokenRequest.allClientsOf(playerId));
                        if (retryFailure == null) {
                            log.info("Revoked all refresh tokens for player {} after re-login", playerId);
                            publishRevocation(playerId, Status.REVOKED_AFTER_RELOGIN, null);
                        } else {
                            notRevoked(playerId, "after re-login: " + retryFailure.reason());
                        }
                    })
                    .exceptionally(error -> {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                        notRevoked(playerId, "browser login did not complete: "
                                + (cause instanceof TimeoutException ? "timed out" : cause.toString()));
                        return null;
                    });
        }
    }

    private void notRevoked(String playerId, String reason) {
        log.warn("Could not revoke refresh tokens for player {} ({}); the ban is applied, but existing sessions remain until their access tokens expire", playerId, reason);
        publishRevocation(playerId, Status.FAILED, reason);
    }

    private void publishRevocation(String playerId, Status status, String detail) {
        applicationEventPublisher.publishEvent(new RefreshTokenRevocationEvent(playerId, status, detail));
    }

    public CompletableFuture<List<BanInfoFX>> getLatestBans() {
        return CompletableFuture.supplyAsync(() -> {
            List<BanInfoFX> allBanInfos = new ArrayList<>();
            int currentPage = 1;
            List<BanInfo> banInfos;

            do {
                banInfos = fafApi.getPage(BanInfo.class, ElideNavigator.of(BanInfo.class)
                                .collection()
                                .addInclude("player")
                                .addInclude("author")
                                .addInclude("revokeAuthor")
                                .addSortingRule("createTime", false),
                        environmentProperties.getMaxPageSizeBans(),
                        currentPage,
                        ImmutableMap.of()
                );

                List<BanInfoFX> mappedBanInfos = banInfos.stream()
                        .map(banInfoMapper::map)
                        .toList();
                allBanInfos.addAll(mappedBanInfos);

                currentPage++;
            } while (banInfos.size() == environmentProperties.getMaxPageSizeBans());

            return allBanInfos;
        });
    }

    public BanInfoFX getBanInfoById(String banInfoId) {
        log.debug("Search for ban id: {}", banInfoId);
        ElideNavigatorOnId<BanInfo> navigator = ElideNavigator.of(BanInfo.class)
                .id(banInfoId)
                .addInclude("player")
                .addInclude("author");
        return banInfoMapper.map(fafApi.getOne(navigator));
    }

    public List<BanInfoFX> getBanInfoByBannedPlayerNameContains(String name) {
        ElideNavigatorOnCollection<BanInfo> navigator = ElideNavigator.of(BanInfo.class)
                .collection()
                .setFilter(ElideNavigator.qBuilder().string("player.login").eq("*" + name + "*"))
                .addInclude("player")
                .addInclude("author");
        return banInfoMapper.mapToFX(fafApi.getAll(BanInfo.class, navigator));
    }
}
