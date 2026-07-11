package com.faforever.moderatorclient.irc;

import org.kitteh.irc.client.library.element.MessageTag.MsgId;
import org.kitteh.irc.client.library.element.MessageTag.Time;
import org.kitteh.irc.client.library.event.helper.ServerMessageEvent;
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent;
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent;
import org.kitteh.irc.client.library.event.client.ClientReceiveNumericEvent;
import org.kitteh.irc.client.library.event.user.UserNickChangeEvent;
import org.kitteh.irc.client.library.event.user.UserQuitEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

final class MessageParser {
    IrcChannelMessageEvent parseMessage(ChannelMessageEvent event, String selfNick) {
        return new IrcChannelMessageEvent(
                event.getChannel().getName(),
                event.getActor().getNick(),
                event.getMessage(),
                event.getTag("msgid", MsgId.class).map(MsgId::getId).orElse(null),
                IrcMessageKind.CHAT,
                event.getActor().getNick().equalsIgnoreCase(selfNick),
                false,
                resolveTimestamp(event)
        );
    }

    IrcChannelNotificationEvent parseJoin(ChannelJoinEvent event) {
        return new IrcChannelNotificationEvent(
                event.getChannel().getName(),
                IrcChannelNotificationType.JOIN,
                event.getUser().getNick(),
                null,
                event.getUser().getNick() + " joined " + event.getChannel().getName(),
                resolveTimestamp(event)
        );
    }

    IrcChannelNotificationEvent parsePart(ChannelPartEvent event) {
        String partMessage = event.getMessage() == null || event.getMessage().isBlank()
                ? event.getUser().getNick() + " left " + event.getChannel().getName()
                : event.getUser().getNick() + " left " + event.getChannel().getName() + " (" + event.getMessage() + ")";
        return new IrcChannelNotificationEvent(
                event.getChannel().getName(),
                IrcChannelNotificationType.PART,
                event.getUser().getNick(),
                null,
                partMessage,
                resolveTimestamp(event)
        );
    }

    IrcChannelNotificationEvent parseQuit(String channel, UserQuitEvent event) {
        String quitMessage = event.getMessage() == null || event.getMessage().isBlank()
                ? event.getUser().getNick() + " quit"
                : event.getUser().getNick() + " quit (" + event.getMessage() + ")";
        return new IrcChannelNotificationEvent(
                channel,
                IrcChannelNotificationType.QUIT,
                event.getUser().getNick(),
                null,
                quitMessage,
                resolveTimestamp(event)
        );
    }

    IrcChannelNotificationEvent parseNickChange(String channel, UserNickChangeEvent event) {
        return new IrcChannelNotificationEvent(
                channel,
                IrcChannelNotificationType.NICK_CHANGE,
                event.getOldUser().getNick(),
                event.getNewUser().getNick(),
                event.getOldUser().getNick() + " is now known as " + event.getNewUser().getNick(),
                resolveTimestamp(event)
        );
    }

    IrcTopicEvent parseTopic(ChannelTopicEvent event) {
        String setter = event.getNewTopic().getSetter()
                .map(actor -> actor.getName())
                .orElse("");
        return new IrcTopicEvent(
                event.getChannel().getName(),
                event.getNewTopic().getValue().orElse(""),
                setter,
                resolveTimestamp(event)
        );
    }

    IrcUserListEvent parseUserList(String channel, List<String> users) {
        return new IrcUserListEvent(channel, List.copyOf(users), Instant.now());
    }

    private Instant resolveTimestamp(ServerMessageEvent event) {
        return event.getTag("time", Time.class).map(Time::getTime).orElse(Instant.now());
    }

    Optional<WhoUser> parseWhoUser(ClientReceiveNumericEvent event) {
        if (event.getNumeric() != 352 && event.getNumeric() != 354) {
            return Optional.empty();
        }

        List<String> parameters = event.getParameters();
        int minimumParameters = event.getNumeric() == 352 ? 8 : 9;
        if (parameters.size() < minimumParameters) {
            return Optional.empty();
        }

        return Optional.of(new WhoUser(
                IrcConfiguration.normalizeChannel(parameters.get(1)),
                parameters.get(5)
        ));
    }

    Optional<String> parseWhoComplete(ClientReceiveNumericEvent event) {
        if (event.getNumeric() != 315) {
            return Optional.empty();
        }

        List<String> parameters = event.getParameters();
        if (parameters.size() < 2) {
            return Optional.empty();
        }

        return Optional.of(IrcConfiguration.normalizeChannel(parameters.get(1)));
    }

    Optional<JoinFailure> parseJoinFailure(ClientReceiveNumericEvent event) {
        if (event.getNumeric() != 404 && event.getNumeric() != 471 && event.getNumeric() != 473
                && event.getNumeric() != 474 && event.getNumeric() != 475 && event.getNumeric() != 477) {
            return Optional.empty();
        }

        List<String> parameters = event.getParameters();
        if (parameters.isEmpty()) {
            return Optional.empty();
        }

        String channel = parameters.size() >= 2 ? parameters.get(1) : "";
        String message = parameters.get(parameters.size() - 1);
        return Optional.of(new JoinFailure(channel, message, event.getNumeric()));
    }

    record JoinFailure(String channel, String message, int numeric) {
    }

    record WhoUser(String channel, String nick) {
    }
}
