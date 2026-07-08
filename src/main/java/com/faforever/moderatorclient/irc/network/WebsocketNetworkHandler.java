package com.faforever.moderatorclient.irc.network;

import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.defaults.feature.network.JavaResolver;
import org.kitteh.irc.client.library.feature.network.NetworkHandler;
import org.kitteh.irc.client.library.feature.network.Resolver;
import org.kitteh.irc.client.library.feature.sts.StsClientState;
import org.kitteh.irc.client.library.feature.sts.StsMachine;
import org.kitteh.irc.client.library.feature.sts.StsPolicy;

import java.util.Optional;

public final class WebsocketNetworkHandler implements NetworkHandler {
    private static final WebsocketNetworkHandler INSTANCE = new WebsocketNetworkHandler();

    private Resolver resolver = new JavaResolver();

    public static WebsocketNetworkHandler getInstance() {
        return INSTANCE;
    }

    private WebsocketNetworkHandler() {
    }

    @Override
    public WebSocketConnection connect(Client.WithManagement client) {
        if (client.getStsMachine().isPresent() && !client.isSecureConnection()) {
            String hostname = client.getServerAddress().getHost();
            StsMachine machine = client.getStsMachine().get();
            Optional<StsPolicy> policy = machine.getStorageManager().getEntry(hostname);
            if (policy.isPresent()) {
                machine.setStsPolicy(policy.get());
                machine.setCurrentState(StsClientState.STS_POLICY_CACHED);
            }
        }

        return new WebSocketConnection(client);
    }

    @Override
    public Resolver getResolver() {
        return this.resolver;
    }

    @Override
    public void setResolver(Resolver resolver) {
        this.resolver = resolver;
    }
}
