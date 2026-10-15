package com.faforever.moderatorclient.api.event;

/** Progress of revoking a banned player's refresh tokens when it needed a moderator re-login. */
public record RefreshTokenRevocationEvent(String playerId, Status status, String detail) {
    public enum Status {
        RELOGIN_REQUIRED,
        REVOKED_AFTER_RELOGIN,
        FAILED
    }
}
