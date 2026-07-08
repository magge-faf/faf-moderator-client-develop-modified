package com.faforever.moderatorclient.irc;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class EventDispatcher {
    private final CopyOnWriteArrayList<Consumer<IrcChannelMessageEvent>> messageListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<IrcConnectionEvent>> connectionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<IrcUserListEvent>> userListListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<IrcEvent>> eventListeners = new CopyOnWriteArrayList<>();

    IrcListenerRegistration addMessageListener(Consumer<IrcChannelMessageEvent> listener) {
        messageListeners.add(listener);
        return () -> messageListeners.remove(listener);
    }

    IrcListenerRegistration addConnectionListener(Consumer<IrcConnectionEvent> listener) {
        connectionListeners.add(listener);
        return () -> connectionListeners.remove(listener);
    }

    IrcListenerRegistration addUserListListener(Consumer<IrcUserListEvent> listener) {
        userListListeners.add(listener);
        return () -> userListListeners.remove(listener);
    }

    IrcListenerRegistration addEventListener(Consumer<IrcEvent> listener) {
        eventListeners.add(listener);
        return () -> eventListeners.remove(listener);
    }

    void dispatch(IrcEvent event) {
        eventListeners.forEach(listener -> listener.accept(event));
        if (event instanceof IrcChannelMessageEvent messageEvent) {
            messageListeners.forEach(listener -> listener.accept(messageEvent));
        } else if (event instanceof IrcConnectionEvent connectionEvent) {
            connectionListeners.forEach(listener -> listener.accept(connectionEvent));
        } else if (event instanceof IrcUserListEvent userListEvent) {
            userListListeners.forEach(listener -> listener.accept(userListEvent));
        }
    }
}
