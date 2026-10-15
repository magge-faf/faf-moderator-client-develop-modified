package com.faforever.moderatorclient.api;

import com.faforever.moderatorclient.login.OAuthValuesReceiver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Renews the moderator session through the browser login while the app keeps running.
 * A browser login is the only way to get a new HMAC into the access token; token refreshes keep the old one.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BrowserReloginService {
    private static final long LOGIN_TIMEOUT_MINUTES = 5;

    private final TokenService tokenService;
    private final OAuthValuesReceiver oAuthValuesReceiver;
    private CompletableFuture<Void> pendingRelogin;

    /** Opens the browser login; concurrent callers share the same pending login. */
    public synchronized CompletableFuture<Void> relogin() {
        if (pendingRelogin != null && !pendingRelogin.isDone()) {
            return pendingRelogin;
        }
        log.info("Opening browser login to renew the moderator session");
        pendingRelogin = oAuthValuesReceiver.receiveValues(tokenService.getEnvironmentProperties())
                .orTimeout(LOGIN_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .thenAccept(values -> tokenService.loginWithAuthorizationCode(values, false))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        // Closes the callback socket so the listener thread does not wait forever.
                        oAuthValuesReceiver.cancelLogin();
                    } else {
                        log.info("Moderator session renewed through browser login");
                    }
                });
        return pendingRelogin;
    }
}
