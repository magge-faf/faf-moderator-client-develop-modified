package com.faforever.moderatorclient.mapstruct;

import com.faforever.commons.api.dto.ModVersion;
import com.faforever.moderatorclient.ui.domain.ModVersionFX;
import org.mapstruct.Mapper;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

@Mapper(componentModel = "spring", uses = {JavaFXMapper.class, ModMapper.class, CycleAvoidingMappingContext.class})
public abstract class ModVersionMapper {
    public abstract ModVersionFX map(ModVersion dto);

    public abstract ModVersion map(ModVersionFX fxBean);

    public abstract List<ModVersionFX> mapToFX(List<ModVersion> dtoList);

    public abstract List<ModVersion> mapToDTO(List<ModVersionFX> fxBeanList);

    protected URI map(URL value) {
        if (value == null) {
            return null;
        }
        try {
            return value.toURI();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL from API: " + value, e);
        }
    }

    protected URL map(URI value) {
        if (value == null) {
            return null;
        }
        try {
            return value.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URI for API: " + value, e);
        }
    }
}
