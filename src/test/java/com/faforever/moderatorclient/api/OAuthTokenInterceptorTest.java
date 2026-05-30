package com.faforever.moderatorclient.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthTokenInterceptorTest {

    @Test
    void addsFreshBearerTokenBeforeExecutingRequest() throws IOException {
        TokenService tokenService = mock();
        when(tokenService.getRefreshedTokenValue()).thenReturn("access-token");
        OAuthTokenInterceptor instance = new OAuthTokenInterceptor(tokenService);
        MockClientHttpRequest request = new MockClientHttpRequest();

        instance.intercept(request, new byte[0], (executedRequest, body) -> {
            assertThat(executedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION), contains("Bearer access-token"));
            return new MockClientHttpResponse(new byte[0], 200);
        });

        verify(tokenService).getRefreshedTokenValue();
    }
}
