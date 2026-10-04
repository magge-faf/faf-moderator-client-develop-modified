package com.faforever.moderatorclient.mapstruct;

import com.faforever.commons.api.dto.AvatarAssignment;
import com.faforever.moderatorclient.ui.domain.AvatarAssignmentFX;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", uses = {JavaFXMapper.class, AvatarMapper.class, PlayerMapper.class, CycleAvoidingMappingContext.class})
public abstract class AvatarAssignmentMapper {
    @Mapping(target = "selected", ignore = true)
    public abstract AvatarAssignmentFX map(AvatarAssignment dto);

    @Mapping(target = "selected", ignore = true)
    public abstract AvatarAssignment map(AvatarAssignmentFX fxBean);

    public abstract List<AvatarAssignmentFX> mapToFX(List<AvatarAssignment> dtoList);

    public abstract List<AvatarAssignment> mapToDto(List<AvatarAssignmentFX> fxBeanList);

    @AfterMapping
    @SuppressWarnings({"deprecation", "removal"}) // Production still exposes this backwards-compatible field.
    protected void mapSelected(AvatarAssignment dto, @MappingTarget AvatarAssignmentFX target) {
        if (dto.getSelected() != null) {
            target.setSelected(dto.getSelected());
        }
    }

    @AfterMapping
    @SuppressWarnings({"deprecation", "removal"}) // Keep the client compatible until currentAvatar is exposed by the API.
    protected void mapSelected(AvatarAssignmentFX source, @MappingTarget AvatarAssignment target) {
        target.setSelected(source.isSelected());
    }
}
