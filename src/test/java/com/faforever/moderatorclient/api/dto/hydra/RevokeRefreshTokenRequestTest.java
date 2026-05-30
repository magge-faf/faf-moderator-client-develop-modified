package com.faforever.moderatorclient.api.dto.hydra;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class RevokeRefreshTokenRequestTest {

    @Test
    void allClientsOfCreatesAllClientRevocationRequest() {
        RevokeRefreshTokenRequest request = RevokeRefreshTokenRequest.allClientsOf("123");

        assertThat(request.getSubject(), is("123"));
        assertThat(request.getClient(), is(nullValue()));
        assertThat(request.isAll(), is(true));
    }
}
