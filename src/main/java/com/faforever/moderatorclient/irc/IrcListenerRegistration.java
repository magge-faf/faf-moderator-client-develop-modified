package com.faforever.moderatorclient.irc;

@FunctionalInterface
public interface IrcListenerRegistration extends AutoCloseable {
    @Override
    void close();
}
