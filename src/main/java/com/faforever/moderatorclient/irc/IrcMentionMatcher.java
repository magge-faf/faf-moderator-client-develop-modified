package com.faforever.moderatorclient.irc;

import java.util.regex.Pattern;

public final class IrcMentionMatcher {
    private IrcMentionMatcher() {
    }

    public static boolean containsMention(String message, String nickname) {
        if (message == null || message.isBlank() || nickname == null || nickname.isBlank()) {
            return false;
        }

        Pattern mentionPattern = Pattern.compile(
                "(^|[^A-Za-z0-9-])" + Pattern.quote(nickname) + "([^A-Za-z0-9-]|$)",
                Pattern.CASE_INSENSITIVE
        );
        return mentionPattern.matcher(message).find();
    }
}
