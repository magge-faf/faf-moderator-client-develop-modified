package com.faforever.moderatorclient.api.domain;

import com.faforever.commons.api.dto.BanInfo;
import com.faforever.commons.api.elide.ElideNavigator;
import com.faforever.commons.api.elide.ElideNavigatorOnCollection;
import com.faforever.commons.api.elide.ElideNavigatorOnId;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.FafUserCommunicationService;
import com.faforever.moderatorclient.api.dto.hydra.RevokeRefreshTokenRequest;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import com.faforever.moderatorclient.mapstruct.BanInfoMapper;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class BanService {
    private static final String REVOKE_ENDPOINT = "/oauth2/revokeTokens";

    private final BanInfoMapper banInfoMapper;
    private final FafApiCommunicationService fafApi;
    private final FafUserCommunicationService fafUser;
    private final EnvironmentProperties environmentProperties;

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
        try {
            fafUser.post(REVOKE_ENDPOINT, RevokeRefreshTokenRequest.allClientsOf(playerId));
        } catch (Exception e) {
            // Token revocation is best-effort; Cloudflare may block the request if no recent browser session
            // exists for this IP. The ban/patch still proceeds — the player's tokens will expire naturally.
            log.warn("Failed to revoke tokens for player {} (ban will still be applied): {}", playerId, e.getMessage());
        }
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
