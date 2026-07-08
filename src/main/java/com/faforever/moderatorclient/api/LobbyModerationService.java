package com.faforever.moderatorclient.api;

import com.faforever.commons.lobby.ConnectionStatus;
import com.faforever.commons.lobby.FafLobbyClient;
import com.faforever.commons.lobby.FafLobbyClient.Config;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class LobbyModerationService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final FafLobbyClient lobbyClient;
    private final LobbyOAuthService lobbyOAuthService;
    private final LobbyUidService lobbyUidService;

    private final AtomicReference<ConnectionStatus> connectionStatus = new AtomicReference<>(ConnectionStatus.DISCONNECTED);

    @PostConstruct
    public void initialize() {
        lobbyClient.getConnectionStatus()
                .doOnNext(connectionStatus::set)
                .doOnError(throwable -> log.warn("Lobby connection status stream failed", throwable))
                .retry()
                .subscribe();
    }

    @EventListener
    public void onHydraAuthorized(com.faforever.moderatorclient.api.event.HydraAuthorizedEvent event) {
        disconnect();
    }

    public void kickFromGame(int playerId) {
        ensureConnected();
        lobbyClient.closePlayerGame(playerId);
    }

    public void kickFromClient(int playerId) {
        ensureConnected();
        lobbyClient.closePlayerLobby(playerId);
    }

    public void kickFromGameAndClient(int playerId) {
        RuntimeException failure = null;

        try {
            kickFromGame(playerId);
        } catch (RuntimeException e) {
            failure = e;
        }

        try {
            kickFromClient(playerId);
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }

    private synchronized void ensureConnected() {
        if (connectionStatus.get() == ConnectionStatus.CONNECTED) {
            return;
        }

        String token = lobbyOAuthService.getRefreshedTokenValue();
        String lobbyAccessUrl = lobbyOAuthService.getLobbyAccessUrl(token);
        String lobbyUserAgent = lobbyOAuthService.getEnvironmentProperties().getLobbyUserAgent();
        String lobbyClientVersion = lobbyOAuthService.getEnvironmentProperties().getLobbyClientVersion();

        Config config = new Config(
                token,
                lobbyClientVersion,
                lobbyUserAgent,
                lobbyAccessUrl,
                sessionId -> lobbyUidService.generateUid(sessionId),
                1024 * 1024,
                false
        );

        lobbyClient.connectAndLogin(config)
                .timeout(CONNECT_TIMEOUT)
                .doOnSubscribe(subscription -> log.info("Connecting moderator client to FAF lobby"))
                .doOnSuccess(player -> {
                    connectionStatus.set(ConnectionStatus.CONNECTED);
                    log.info("Connected moderator client to FAF lobby as {}", player.getLogin());
                })
                .doOnError(throwable -> {
                    connectionStatus.set(ConnectionStatus.DISCONNECTED);
                    log.warn("Failed to connect moderator client to FAF lobby", throwable);
                })
                .block();
    }

    private synchronized void disconnect() {
        try {
            lobbyClient.disconnect();
        } catch (RuntimeException e) {
            log.debug("Ignoring lobby disconnect failure", e);
        } finally {
            connectionStatus.set(ConnectionStatus.DISCONNECTED);
        }
    }
}
