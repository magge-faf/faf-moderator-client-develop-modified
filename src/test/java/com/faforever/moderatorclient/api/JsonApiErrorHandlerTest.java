package com.faforever.moderatorclient.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
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

    @Test
    void handleErrorLogsStatusOnlyAtWarnAndRedactedTruncatedBodyAtDebug() throws Exception {
        JsonApiErrorHandler errorHandler = new JsonApiErrorHandler(mock());
        ClientHttpResponse response = mock();
        String secret = "super-secret-refresh-token";
        String body = "{\"refresh_token\":\"" + secret + "\",\"detail\":\"" + "x".repeat(700) + "\"}";
        when(response.getBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(response.getStatusText()).thenReturn("Internal Server Error");

        Logger logger = (Logger) LoggerFactory.getLogger(JsonApiErrorHandler.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        try {
            assertThrows(
                    HttpServerErrorException.class,
                    () -> errorHandler.handleError(response, HttpStatus.INTERNAL_SERVER_ERROR, URI.create("https://example.test"), HttpMethod.GET));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }

        assertThat(appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList(), everyItem(not(containsString(secret))));
        assertThat(appender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .map(ILoggingEvent::getFormattedMessage)
                .findFirst()
                .orElseThrow(), not(containsString(secret)));
        assertThat(appender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .map(ILoggingEvent::getFormattedMessage)
                .findFirst()
                .orElseThrow(), containsString("[truncated]"));
    }
}
