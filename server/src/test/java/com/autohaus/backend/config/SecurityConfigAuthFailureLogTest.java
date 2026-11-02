package com.autohaus.backend.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-037 AC-21, second half: failed Basic-Auth attempts on {@code /internal/**} must be visible
 * as {@code WARN} with a masked IP — and must never leak the user name, the password or the
 * {@code User-Agent} (BR-3). / Zweite Hälfte von AC-21: Fehlversuche als WARN mit maskierter IP,
 * ohne Benutzername, Passwort oder User-Agent.
 *
 * <p>The listener is a plain {@code ApplicationListener} bean, so it is exercised directly with a
 * real {@link AuthenticationFailureBadCredentialsEvent} and a Logback {@link ListAppender} —
 * no Spring context needed. / Der Listener wird direkt mit einem echten Event geprüft.
 */
class SecurityConfigAuthFailureLogTest {

    private static final String REAL_PASSWORD = "sup3r-secret-pw";
    private static final String REAL_USER = "attacker";

    private Logger securityConfigLogger;
    private ListAppender<ILoggingEvent> appender;
    private ApplicationListener<AuthenticationFailureBadCredentialsEvent> listener;

    @BeforeEach
    void setUp() {
        SecurityConfig config = new SecurityConfig("'self'", new ErrorReportingProperties(
            "workspace", "0 0 3 * * *", "Europe/Berlin",
            new ErrorReportingProperties.Admin("admin", "pw")));
        listener = config.internalAuthFailureLogger();

        securityConfigLogger = (Logger) LoggerFactory.getLogger(SecurityConfig.class);
        appender = new ListAppender<>();
        appender.start();
        securityConfigLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        securityConfigLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("AC-21: a failed attempt is logged as WARN with a masked IPv4 (/24)")
    void logsAWarningWithAMaskedIpv4() {
        listener.onApplicationEvent(failureFrom("203.0.113.42"));

        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size(), "exactly one line per failed attempt");
        ILoggingEvent event = events.get(0);

        assertEquals(Level.WARN, event.getLevel(), "AC-21 requires WARN, not INFO/DEBUG");
        String line = event.getFormattedMessage();
        assertTrue(line.contains("203.0.113.0"), "the IP must be masked to /24, was: " + line);
        assertFalse(line.contains("203.0.113.42"), "the raw IP must never appear: " + line);
    }

    @Test
    @DisplayName("AC-21 / BR-3: the line carries neither user name, password nor user agent")
    void neverLeaksCredentialsOrUserAgent() {
        listener.onApplicationEvent(failureFrom("203.0.113.42"));

        String line = appender.list.get(0).getFormattedMessage();
        assertFalse(line.contains(REAL_USER), "user name must not be logged: " + line);
        assertFalse(line.contains(REAL_PASSWORD), "password must not be logged: " + line);
        assertFalse(line.toLowerCase().contains("mozilla"), "no user agent (BR-3): " + line);
    }

    @Test
    @DisplayName("AC-21: an event without web details degrades to 'unknown' instead of throwing")
    void handlesAMissingRemoteAddress() {
        Authentication auth = new UsernamePasswordAuthenticationToken(REAL_USER, REAL_PASSWORD);
        listener.onApplicationEvent(new AuthenticationFailureBadCredentialsEvent(
            auth, new BadCredentialsException("bad")));

        String line = appender.list.get(0).getFormattedMessage();
        assertTrue(line.contains("unknown"), "missing details must not blow up: " + line);
    }

    /** Builds a realistic Basic-Auth failure carrying the client IP in the web details. */
    private static AuthenticationFailureBadCredentialsEvent failureFrom(String remoteAddress) {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(REAL_USER, REAL_PASSWORD);
        WebAuthenticationDetails details = mock(WebAuthenticationDetails.class);
        when(details.getRemoteAddress()).thenReturn(remoteAddress);
        auth.setDetails(details);
        return new AuthenticationFailureBadCredentialsEvent(auth, new BadCredentialsException("bad"));
    }
}
