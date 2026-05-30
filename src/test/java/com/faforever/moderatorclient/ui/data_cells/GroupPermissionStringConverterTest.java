package com.faforever.moderatorclient.ui.data_cells;

import com.faforever.moderatorclient.ui.domain.GroupPermissionFX;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupPermissionStringConverterTest {

    private final GroupPermissionStringConverter instance = new GroupPermissionStringConverter();

    @Test
    void convertsPermissionToTechnicalName() {
        GroupPermissionFX permission = new GroupPermissionFX();
        permission.setTechnicalName("ROLE_MODERATOR");

        assertThat(instance.toString(permission), is("ROLE_MODERATOR"));
    }

    @Test
    void convertsNullToEmptyString() {
        assertThat(instance.toString(null), is(""));
    }

    @Test
    void doesNotSupportParsingFromString() {
        assertThrows(UnsupportedOperationException.class, () -> instance.fromString("ROLE_MODERATOR"));
    }
}
