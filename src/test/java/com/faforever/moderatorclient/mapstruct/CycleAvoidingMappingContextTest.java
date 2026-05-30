package com.faforever.moderatorclient.mapstruct;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class CycleAvoidingMappingContextTest {

    private final CycleAvoidingMappingContext instance = new CycleAvoidingMappingContext();

    @Test
    void storesAndRetrievesMappedInstanceBySourceIdentity() {
        String source = new String("same value");
        Object target = new Object();

        instance.storeMappedInstance(source, target);

        assertThat(instance.getMappedInstance(source, Object.class), sameInstance(target));
        assertThat(instance.getMappedInstance(new String("same value"), Object.class), is(nullValue()));
    }

    @Test
    void clearCacheDropsStoredInstances() {
        Object source = new Object();
        Object target = new Object();
        instance.storeMappedInstance(source, target);

        instance.clearCache();

        assertThat(instance.getMappedInstance(source, Object.class), is(nullValue()));
    }
}
