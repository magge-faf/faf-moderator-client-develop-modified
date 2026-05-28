package com.faforever.moderatorclient.api;

import com.faforever.commons.api.dto.MapVersion;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

class JsonApiMessageConverterTest {

    @Test
    void supportsElideEntitiesAndArraysOfElideEntities() {
        JsonApiMessageConverter instance = new JsonApiMessageConverter(mock());

        assertThat(instance.supports(MapVersion.class), is(true));
        assertThat(instance.supports(MapVersion[].class), is(true));
    }

    @Test
    void rejectsClassesOutsideJsonApiDomain() {
        JsonApiMessageConverter instance = new JsonApiMessageConverter(mock());

        assertThat(instance.supports(String.class), is(false));
        assertThat(instance.supports(String[].class), is(false));
    }
}
