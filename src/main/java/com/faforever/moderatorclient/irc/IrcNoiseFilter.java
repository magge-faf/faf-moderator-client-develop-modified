package com.faforever.moderatorclient.irc;

public final class IrcNoiseFilter {
    private static final String HISTORY_SERVER_PREFIX = "HistServ";

    private IrcNoiseFilter() {
    }

    public static boolean countsAsUnread(IrcMessageEntry entry) {
        return !isHiddenSystemMessage(entry);
    }

    public static boolean isHiddenSystemMessage(IrcMessageEntry entry) {
        return isHiddenSystemMessage(entry.channel(), entry.sender(), entry.kind(), entry.notificationType(), entry.text());
    }

    public static boolean isHiddenSystemMessage(String channel, String sender, IrcMessageKind kind,
                                                IrcChannelNotificationType notificationType, String text) {
        if (notificationType != null) {
            return true;
        }
        if (kind != IrcMessageKind.CHAT) {
            return true;
        }
        if (isHistoryServerMessage(sender)) {
            return true;
        }

        String normalizedChannel = safe(channel);
        String normalizedText = safe(text).trim();

        if (normalizedText.isBlank()) {
            return false;
        }

        return normalizedText.startsWith("Topic: ")
                || normalizedText.endsWith(" joined " + normalizedChannel)
                || normalizedText.endsWith(" left " + normalizedChannel)
                || normalizedText.endsWith(" joined the channel")
                || normalizedText.contains(" is now known as ")
                || normalizedText.matches(".+ quit( \\(.+\\))?$");
    }

    public static boolean isHiddenSystemMessage(IrcChannelMessageEvent event) {
        if (event == null) {
            return false;
        }
        return isHiddenSystemMessage(event.channel(), event.author(), event.kind(), null, event.message());
    }

    public static boolean isHistoryServerMessage(String sender) {
        return safe(sender).startsWith(HISTORY_SERVER_PREFIX);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
