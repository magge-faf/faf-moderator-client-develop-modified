package com.faforever.moderatorclient.irc;

import com.faforever.moderatorclient.irc.network.WebsocketNetworkHandler;
import lombok.extern.slf4j.Slf4j;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.feature.auth.SaslPlain;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
final class ConnectionManager {
    private final AtomicReference<Client> clientRef = new AtomicReference<>();

    void connect(IrcConfiguration configuration, String username, String ircToken, Object eventListener,
                 Consumer<String> inputListener,
                 Consumer<String> outputListener, Consumer<Exception> exceptionListener) {
        Client.WithManagement client = (Client.WithManagement) Client.builder()
                .name("faf-moderator-client")
                .nick(configuration.nickname())
                .user(username)
                .realName(username)
                .server()
                .host(configuration.host())
                .port(configuration.port(), Client.Builder.Server.SecurityType.SECURE)
                .then()
                .listeners()
                .input(inputListener)
                .output(outputListener)
                .exception(exceptionListener)
                .then()
                .management()
                .networkHandler(WebsocketNetworkHandler.getInstance())
                .then()
                .build();
        client.getActorTracker().setQueryChannelInformation(false);
        client.getEventManager().registerEventListener(eventListener);
        client.getAuthManager().addProtocol(new SaslPlain(client, "%s@FAF".formatted(username), "token:%s".formatted(ircToken)));

        Client previous = clientRef.getAndSet(client);
        if (previous != null) {
            safeShutdown(previous, "Replacing IRC connection");
        }

        client.connect();
    }

    Optional<Client> getClient() {
        return Optional.ofNullable(clientRef.get());
    }

    void disconnect(String reason) {
        Client client = clientRef.getAndSet(null);
        if (client != null) {
            safeShutdown(client, reason);
        }
    }

    void addChannel(String channel) {
        getClient().ifPresent(client -> client.addChannel(channel));
    }

    void removeChannel(String channel, String reason) {
        getClient().ifPresent(client -> client.removeChannel(channel, reason));
    }

    void sendMessage(String channel, String message) {
        getClient().ifPresent(client -> client.sendMessage(channel, message));
    }

    void sendRawLine(String rawLine) {
        getClient().ifPresent(client -> client.sendRawLine(rawLine));
    }

    private void safeShutdown(Client client, String reason) {
        try {
            client.shutdown(reason);
        } catch (RuntimeException ex) {
            log.warn("Failed to shutdown IRC client cleanly", ex);
        }
    }
}
