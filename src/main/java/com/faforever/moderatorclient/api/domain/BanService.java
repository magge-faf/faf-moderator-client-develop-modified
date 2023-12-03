package com.faforever.moderatorclient.api.domain;

import com.faforever.commons.api.dto.BanInfo;
import com.faforever.commons.api.elide.ElideNavigator;
import com.faforever.commons.api.elide.ElideNavigatorOnCollection;
import com.faforever.commons.api.elide.ElideNavigatorOnId;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.FafUserCommunicationService;
import com.faforever.moderatorclient.api.dto.hydra.RevokeRefreshTokenRequest;
import com.faforever.moderatorclient.mapstruct.BanInfoMapper;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BanService {
    private static final String REVOKE_ENDPOINT = "/oauth2/revokeTokens";

    private final BanInfoMapper banInfoMapper;
    private final FafApiCommunicationService fafApi;
    private final FafUserCommunicationService fafUser;

    public BanInfo patchBanInfo(@NotNull BanInfoFX banInfoFX) {
        BanInfo banInfo = banInfoMapper.map(banInfoFX);
        log.debug("Patching BanInfo of id: {}", banInfo.getId());
        fafUser.post(REVOKE_ENDPOINT, RevokeRefreshTokenRequest.allClientsOf(banInfo.getPlayer().getId()));
        banInfo.setAuthor(null);
        banInfo.setPlayer(null);
        return fafApi.patch(ElideNavigator.of(BanInfo.class).id(banInfo.getId()), banInfo);
    }

    public String createBan(@NotNull BanInfoFX banInfoFX) {
        BanInfo banInfo = banInfoMapper.map(banInfoFX);
        log.debug("Creating ban");
        fafUser.post(REVOKE_ENDPOINT, RevokeRefreshTokenRequest.allClientsOf(banInfo.getPlayer().getId()));
        return fafApi.post(ElideNavigator.of(BanInfo.class).collection(), banInfo).getId();
    }

    public void updateBan(BanInfo banInfoUpdate) {
        log.debug("Update for ban id: " + banInfoUpdate.getId());
        ElideNavigatorOnId<BanInfo> navigator = ElideNavigator.of(BanInfo.class)
                .id(banInfoUpdate.getId());

        fafUser.post(REVOKE_ENDPOINT, RevokeRefreshTokenRequest.allClientsOf(banInfoUpdate.getPlayer().getId()));
        fafApi.patch(navigator, banInfoUpdate);
    }

    public CompletableFuture<List<BanInfoFX>> getLatestBans() {
        return CompletableFuture.supplyAsync(() -> {
            List<BanInfo> banInfos = fafApi.getPage(BanInfo.class, ElideNavigator.of(BanInfo.class)
                            .collection()
                            .addInclude("player")
                            .addInclude("author")
                            .addInclude("revokeAuthor")
                            .addSortingRule("createTime", false),
                    10000,
                    1,
                    ImmutableMap.of()
            );

            int totalBans = banInfos.size();
            long permanentBans = banInfos.stream()
                    .filter(info -> "PERMANENT".equalsIgnoreCase(String.valueOf(info.getDuration())))
                    .count();
            long temporaryBans = banInfos.stream()
                    .filter(info -> "TEMPORARY".equalsIgnoreCase(String.valueOf(info.getDuration())))
                    .count();

            Map<String, Long> modPermanentBansCount = banInfos.stream()
                    .filter(info -> "PERMANENT".equalsIgnoreCase(String.valueOf(info.getDuration())))
                    .collect(Collectors.groupingBy(info -> info.getAuthor().getLogin(), Collectors.counting()));

            Map<String, Long> modTemporaryBansCount = banInfos.stream()
                    .filter(info -> "TEMPORARY".equalsIgnoreCase(String.valueOf(info.getDuration())))
                    .collect(Collectors.groupingBy(info -> info.getAuthor().getLogin(), Collectors.counting()));

            System.out.println("\nModerator Permanent Bans Count (Descending Order; Ignore 2 or less):");
            modPermanentBansCount.entrySet().stream()
                    .filter(entry -> entry.getValue() > 2)
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry ->
                            System.out.println("Moderator: " + entry.getKey() + ", Permanent Bans Count: " + entry.getValue()));

            System.out.println("\nModerator Temporary Bans Count (Descending Order; Ignore 2 or less):");
            modTemporaryBansCount.entrySet().stream()
                    .filter(entry -> entry.getValue() > 2)
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry ->
                            System.out.println("Moderator: " + entry.getKey() + ", Temporary Bans Count: " + entry.getValue()));

            System.out.println("\n");
            System.out.println("Total Bans: " + totalBans);
            System.out.println("Permanent Bans: " + permanentBans);
            System.out.println("Temporary Bans: " + temporaryBans);

            return banInfos.stream().map(banInfoMapper::map).collect(Collectors.toList());
        });
    }

    public BanInfoFX getBanInfoById(String banInfoId) {
        log.debug("Search for ban id: " + banInfoId);
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
