package com.faforever.moderatorclient.mapstruct;

import com.faforever.commons.api.dto.Game;
import com.faforever.moderatorclient.ui.domain.GameFX;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = {JavaFXMapper.class, GamePlayerStatsMapper.class, PlayerMapper.class, FeaturedModMapper.class, MapVersionMapper.class})
public abstract class GameMapper {
    public abstract GameFX map(Game dto);

    public abstract Game map(GameFX fxBean);

    public abstract List<GameFX> map(List<Game> dtoList);
}
