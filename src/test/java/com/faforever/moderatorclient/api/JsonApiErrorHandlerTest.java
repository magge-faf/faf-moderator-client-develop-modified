package com.faforever.moderatorclient.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpServerErrorException;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonApiErrorHandlerTest {

    @Test
    void handleErrorPreservesBodyForDefaultErrorHandlingAfterLogging() throws Exception {
        JsonApiErrorHandler errorHandler = new JsonApiErrorHandler(mock());
        ClientHttpResponse response = mock();
        byte[] body = "{\"errors\":[{\"detail\":\"server failed\"}]}".getBytes(StandardCharsets.UTF_8);
        when(response.getBody()).thenReturn(new ByteArrayInputStream(body));
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(response.getStatusText()).thenReturn("Internal Server Error");

        HttpServerErrorException exception = assertThrows(
                HttpServerErrorException.class,
                () -> errorHandler.handleError(response, HttpStatus.INTERNAL_SERVER_ERROR, URI.create("https://example.test"), HttpMethod.GET));

        assertThat(exception.getResponseBodyAsString(), containsString("server failed"));
    }
}
