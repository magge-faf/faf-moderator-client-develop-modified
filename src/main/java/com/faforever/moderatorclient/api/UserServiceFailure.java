package com.faforever.moderatorclient.api;

/**
 * Why a best-effort user service request failed.
 *
 * @param cloudflareChallenge true when Cloudflare answered with a challenge instead of forwarding the request,
 *                            which happens when the X-HMAC header is missing or too old
 */
public record UserServiceFailure(String reason, boolean cloudflareChallenge) {
    @Override
    public String toString() {
        return reason;
    }
}
