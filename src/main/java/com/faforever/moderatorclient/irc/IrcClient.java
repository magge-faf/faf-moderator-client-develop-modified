package com.faforever.moderatorclient.irc;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface IrcClient {
    void connect();

    void connect(IrcConfiguration configuration);

    void disconnect();

    void joinChannel(String channel);

    void leaveChannel(String channel);

    void openPrivateConversation(String username);

    void sendMessage(String channel, String message);

    void markChannelRead(String channel);

    IrcConnectionState getConnectionState();

    Optional<IrcConfiguration> getConfiguration();

    Optional<IrcChannelSnapshot> getChannelSnapshot(String channel);

    List<IrcChannelSnapshot> getChannelSnapshots();

    IrcListenerRegistration addMessageListener(Consumer<IrcChannelMessageEvent> listener);

    IrcListenerRegistration addConnectionListener(Consumer<IrcConnectionEvent> listener);

    IrcListenerRegistration addUserListListener(Consumer<IrcUserListEvent> listener);

    IrcListenerRegistration addEventListener(Consumer<IrcEvent> listener);
}
