package com.faforever.moderatorclient.mapstruct;

import com.faforever.commons.api.dto.BanInfo;
import com.faforever.commons.api.dto.Player;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = {JavaFXMapper.class, UniqueIdAssignmentMapper.class, NameRecordMapper.class, AvatarAssignmentMapper.class, AccountLinkMapper.class, CycleAvoidingMappingContext.class})
public abstract class PlayerMapper {
    @Mapping(target = "bans", ignore = true)
    public abstract PlayerFX map(Player dto);

    @Mapping(target = "bans", ignore = true)
    public abstract Player map(PlayerFX fxBean);

    public abstract List<PlayerFX> mapToFx(List<Player> dtoList);

    public abstract List<Player> mapToDto(List<PlayerFX> fxBeanList);

    public abstract Set<PlayerFX> mapToFx(Set<Player> dtoList);

    public abstract Set<Player> mapToDto(Set<PlayerFX> fxBeanList);

    @AfterMapping
    protected void mapBans(Player dto, @MappingTarget PlayerFX playerFX) {
        if (dto == null || dto.getBans() == null) {
            return;
        }

        playerFX.getBans().clear();
        dto.getBans().forEach(ban -> playerFX.getBans().add(mapBanInfo(ban, playerFX)));
    }

    private BanInfoFX mapBanInfo(BanInfo dto, PlayerFX mappedPlayer) {
        if (dto == null) {
            return null;
        }

        BanInfoFX ban = new BanInfoFX();
        ban.setId(dto.getId());
        ban.setCreateTime(dto.getCreateTime());
        ban.setUpdateTime(dto.getUpdateTime());
        ban.setPlayer(mappedPlayer);
        ban.setAuthor(mapShallowPlayer(dto.getAuthor()));
        ban.setReason(dto.getReason());
        ban.setExpiresAt(dto.getExpiresAt());
        ban.setLevel(dto.getLevel());
        ban.setRevokeReason(dto.getRevokeReason());
        ban.setRevokeAuthor(mapShallowPlayer(dto.getRevokeAuthor()));
        ban.setRevokeTime(dto.getRevokeTime());
        return ban;
    }

    private PlayerFX mapShallowPlayer(Player dto) {
        if (dto == null) {
            return null;
        }

        PlayerFX player = new PlayerFX();
        player.setId(dto.getId());
        player.setCreateTime(dto.getCreateTime());
        player.setUpdateTime(dto.getUpdateTime());
        player.setLogin(dto.getLogin());
        player.setEmail(dto.getEmail());
        player.setUserAgent(dto.getUserAgent());
        player.setRecentIpAddress(dto.getRecentIpAddress());
        player.setLastLogin(dto.getLastLogin());
        return player;
    }
}
