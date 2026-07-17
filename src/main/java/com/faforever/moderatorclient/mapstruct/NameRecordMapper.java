package com.faforever.moderatorclient.mapstruct;

import com.faforever.commons.api.dto.NameRecord;
import com.faforever.moderatorclient.ui.domain.NameRecordFX;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = JavaFXMapper.class)
public abstract class NameRecordMapper {
    @Mapping(target = "player", ignore = true)
    public abstract NameRecordFX map(NameRecord dto);

    @Mapping(target = "player", ignore = true)
    public abstract NameRecord map(NameRecordFX fxBean);

    public abstract List<NameRecordFX> mapToFX(List<NameRecord> dtoList);

    public abstract List<NameRecord> mapToDto(List<NameRecordFX> fxBeanList);
}
