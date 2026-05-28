package com.faforever.moderatorclient.login;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class KnownLoginErrorExceptionTest {

    @Test
    void exposesMessageAndI18nKey() {
        KnownLoginErrorException exception = new KnownLoginErrorException("Scope denied", "login.scopeDenied");

        assertThat(exception.getMessage(), is("Scope denied"));
        assertThat(exception.getI18nKey(), is("login.scopeDenied"));
    }
}
