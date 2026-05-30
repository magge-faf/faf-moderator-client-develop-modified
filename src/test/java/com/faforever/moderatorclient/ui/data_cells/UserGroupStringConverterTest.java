package com.faforever.moderatorclient.ui.data_cells;

import com.faforever.moderatorclient.ui.domain.UserGroupFX;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserGroupStringConverterTest {

    private final UserGroupStringConverter instance = new UserGroupStringConverter();

    @Test
    void convertsUserGroupToTechnicalName() {
        UserGroupFX group = new UserGroupFX();
        group.setTechnicalName("moderators");

        assertThat(instance.toString(group), is("moderators"));
    }

    @Test
    void convertsNullToEmptyString() {
        assertThat(instance.toString(null), is(""));
    }

    @Test
    void doesNotSupportParsingFromString() {
        assertThrows(UnsupportedOperationException.class, () -> instance.fromString("moderators"));
    }
}
