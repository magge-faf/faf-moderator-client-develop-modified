package com.faforever.moderatorclient.login;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthValuesReceiverTest {

  @Test
  void checkForErrorDoesNotExposeFullCallbackRequest() {
    OAuthValuesReceiver receiver = new OAuthValuesReceiver();
    String request = "GET /?error=scope_denied&state=secret-state&code=secret-code HTTP/1.1";

    KnownLoginErrorException exception = assertThrows(
        KnownLoginErrorException.class,
        () -> ReflectionTestUtils.invokeMethod(receiver, "checkForError", request));

    assertThat(exception.getMessage(), containsString("scope_denied"));
    assertThat(exception.getMessage(), not(containsString("secret-state")));
    assertThat(exception.getMessage(), not(containsString("secret-code")));
    assertThat(exception.getMessage(), not(containsString("/?error=")));
  }

  @Test
  void readValuesDoesNotExposeCallbackRequestWhenRequiredParameterIsMissing() {
    OAuthValuesReceiver receiver = new OAuthValuesReceiver();
    String request = "GET /?state=secret-state&code=secret-code HTTP/1.1";

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> ReflectionTestUtils.invokeMethod(receiver, "extractValue", request, Pattern.compile("missing=([^ &]+)")));

    assertThat(exception.getMessage(), is("Could not extract required OAuth callback parameter"));
  }

  @Test
  void writeResponseUsesUtf8ContentHeaders() {
    OAuthValuesReceiver receiver = new OAuthValuesReceiver();
    CapturingSocket socket = new CapturingSocket();

    ReflectionTestUtils.invokeMethod(receiver, "writeResponse", socket, true);

    String response = socket.response();
    String[] responseParts = response.split("\r\n\r\n", 2);
    Matcher contentLength = Pattern.compile("Content-Length: (\\d+)").matcher(responseParts[0]);

    assertThat(responseParts[0], containsString("Content-Length: "));
    assertThat(responseParts[0], containsString("Content-Type: text/html; charset=UTF-8"));
    assertThat(contentLength.find(), is(true));
    assertThat(Integer.parseInt(contentLength.group(1)), is(responseParts[1].getBytes(StandardCharsets.UTF_8).length));
  }

  private static class CapturingSocket extends Socket {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    @Override
    public OutputStream getOutputStream() throws IOException {
      return outputStream;
    }

    private String response() {
      return outputStream.toString(StandardCharsets.UTF_8);
    }
  }
}
