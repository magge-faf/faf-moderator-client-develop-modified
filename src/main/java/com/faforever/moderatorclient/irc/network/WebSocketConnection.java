package com.faforever.moderatorclient.irc.network;

import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.LineEncoder;
import io.netty.handler.codec.string.LineSeparator;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.event.connection.ClientConnectionClosedEvent;
import org.kitteh.irc.client.library.event.connection.ClientConnectionEndedEvent;
import org.kitteh.irc.client.library.event.connection.ClientConnectionEstablishedEvent;
import org.kitteh.irc.client.library.event.connection.ClientConnectionFailedEvent;
import org.kitteh.irc.client.library.exception.KittehConnectionException;
import org.kitteh.irc.client.library.feature.defaultmessage.DefaultMessageType;
import org.kitteh.irc.client.library.feature.network.ClientConnection;
import org.kitteh.irc.client.library.util.HostWithPort;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class WebSocketConnection implements ClientConnection {
    private static final int MAX_LINE_LENGTH = 9001;

    private final Client.WithManagement client;
    private final Sinks.Many<String> outboundSink = Sinks.many().unicast().onBackpressureBuffer();
    private final Flux<String> outboundMessages = outboundSink.asFlux().publish().autoConnect();

    private volatile boolean reconnect = true;
    private volatile Disposable ping;
    private volatile Connection connection;
    private volatile String lastMessage;
    private volatile boolean alive = true;
    private volatile Disposable reconnectSubscription;

    public WebSocketConnection(Client.WithManagement client) {
        this.client = client;
        HostWithPort serverAddress = this.client.getServerAddress();
        String scheme = client.isSecureConnection() ? "wss" : "ws";
        HttpClient.newConnection()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .doOnConnected(currentConnection -> {
                    this.connection = currentConnection;
                    currentConnection.addHandlerFirst(new LineEncoder(LineSeparator.UNIX))
                            .addHandlerLast(new LineBasedFrameDecoder(MAX_LINE_LENGTH));
                    this.client.getEventManager().callEvent(new ClientConnectionEstablishedEvent(this.client));
                    this.client.beginMessageSendingImmediate(message -> outboundSink.emitNext(
                            message, Sinks.EmitFailureHandler.busyLooping(Duration.ofMinutes(1))));
                    this.alive = true;
                })
                .doOnDisconnected(currentConnection -> {
                    this.client.getEventManager()
                            .callEvent(new ClientConnectionClosedEvent(this.client, reconnect, null, lastMessage));
                    this.client.pauseMessageSending();
                    if (this.ping != null) {
                        this.ping.dispose();
                    }
                    this.alive = false;
                    ClientConnectionEndedEvent event =
                            new ClientConnectionClosedEvent(this.client, this.reconnect, null, this.lastMessage);
                    this.client.getEventManager().callEvent(event);
                    if (event.willAttemptReconnect()) {
                        scheduleReconnect(event.getReconnectionDelay());
                    }
                })
                .doOnResolveError((currentConnection, throwable) -> {
                    this.client.getEventManager()
                            .callEvent(new ClientConnectionFailedEvent(this.client, reconnect, throwable));
                    this.client.getExceptionListener().queue(new KittehConnectionException(throwable, false));
                })
                .websocket()
                .uri(URI.create("%s://%s:%d".formatted(scheme, serverAddress.getHost(), serverAddress.getPort())))
                .handle((inbound, outbound) -> {
                    outbound.sendString(outboundMessages
                                    .doOnNext(message -> this.client.getOutputListener().queue(message))
                                    .doOnError(this::handleException))
                            .then()
                            .subscribeOn(Schedulers.single())
                            .subscribe();

                    return inbound.receive().asString(StandardCharsets.UTF_8).doOnError(this::handleException);
                })
                .subscribe(message -> {
                    this.client.getInputListener().queue(message);
                    this.client.processLine(message);
                    this.lastMessage = message;
                });
    }

    private void scheduleReconnect(int delay) {
        if (reconnectSubscription != null) {
            reconnectSubscription.dispose();
        }
        reconnectSubscription = Mono.delay(Duration.ofMillis(delay))
                .doOnNext(ignored -> {
                    if (reconnect) {
                        this.client.connect();
                    }
                })
                .subscribe();
    }

    private void handleException(Throwable thrown) {
        if (thrown instanceof Exception exception) {
            this.client.getExceptionListener().queue(exception);
            if (thrown instanceof IOException) {
                shutdown(DefaultMessageType.QUIT_INTERNAL_EXCEPTION, true);
            }
        }
    }

    @Override
    public boolean isAlive() {
        return this.alive;
    }

    @Override
    public void startPing() {
        this.ping = Flux.interval(Duration.ofSeconds(60)).doOnNext(ignored -> this.client.ping()).subscribe();
    }

    @Override
    public void shutdown(DefaultMessageType messageType, boolean reconnect) {
        shutdown(this.client.getDefaultMessageMap().getDefault(messageType).orElse(null), reconnect);
    }

    @Override
    public void shutdown(String message, boolean reconnect) {
        this.reconnect = reconnect;
        if (reconnectSubscription != null) {
            reconnectSubscription.dispose();
            reconnectSubscription = null;
        }
        this.client.pauseMessageSending();
        outboundSink.emitNext("QUIT" + (message != null ? " :" + message : ""),
                Sinks.EmitFailureHandler.busyLooping(Duration.ofMinutes(1)));
        if (this.connection != null) {
            this.connection.dispose();
        }
    }
}
