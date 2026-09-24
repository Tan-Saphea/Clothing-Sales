package com.clothing.app.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventsListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventsListener.class);
    private final LoginAttemptService loginAttemptService;

    public AuthenticationEventsListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() != null && event.getAuthentication().getName() != null) {
            String username = event.getAuthentication().getName();
            loginAttemptService.loginSucceeded(username);
            log.debug("Authentication succeeded for '{}'", username);
        }
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        if (event.getAuthentication() != null && event.getAuthentication().getName() != null) {
            String username = event.getAuthentication().getName();
            loginAttemptService.loginFailed(username);
            log.warn("Authentication failed for '{}' ({})", username, event.getException().getMessage());
        }
    }
}
