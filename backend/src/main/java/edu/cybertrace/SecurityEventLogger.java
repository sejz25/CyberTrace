package edu.cybertrace;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/** Records failed sign-ins and denied API actions without ever storing passwords. */
@Component
class FailedLoginLogger {
    private final ForensicsService forensics;
    FailedLoginLogger(ForensicsService forensics) { this.forensics = forensics; }

    @EventListener
    void failedSignIn(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication() == null ? "unknown" : event.getAuthentication().getName();
        forensics.audit("ANONYMOUS", "LOGIN", "user:" + username, "FAILED", "Invalid sign-in credentials");
    }
}

@RestControllerAdvice
class ApiErrorLogger {
    private final ForensicsService forensics;
    ApiErrorLogger(ForensicsService forensics) { this.forensics = forensics; }

    @ExceptionHandler(ResponseStatusException.class)
    org.springframework.http.ResponseEntity<Map<String,String>> handle(ResponseStatusException error,
                                                                       Authentication authentication,
                                                                       HttpServletRequest request) {
        if (error.getStatusCode().value() == 403) {
            String actor = authentication == null ? "ANONYMOUS" : authentication.getName();
            forensics.audit(actor, "AUTHORIZATION_DENIED", request.getRequestURI(), "BLOCKED",
                    "The server rejected an action outside the account's permission");
        }
        int status = error.getStatusCode().value();
        String message = error.getReason() == null ? "Request could not be completed" : error.getReason();
        return org.springframework.http.ResponseEntity.status(status).body(Map.of("message", message));
    }
}
