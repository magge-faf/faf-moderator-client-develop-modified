package com.faforever.moderatorclient.ui;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PlatformServiceImplTest {

    @Test
    void platformServiceIsStandaloneSpringComponent() throws Exception {
        assertThat(PlatformServiceImpl.class.isAnnotationPresent(Component.class), is(true));
        assertThat(PlatformServiceImpl.class.getDeclaredConstructor().getParameterCount(), is(0));
    }
}
