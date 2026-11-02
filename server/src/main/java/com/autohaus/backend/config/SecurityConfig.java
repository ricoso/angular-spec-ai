package com.autohaus.backend.config;

import com.autohaus.backend.common.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Basic-Auth realm of the internal status page (REQ-037 §12.2, E5). Set explicitly instead of
     * the Spring default {@code "Realm"} so the {@code WWW-Authenticate} header names the resource.
     * / Expliziter Realm statt Spring-Default.
     */
    private static final String INTERNAL_REALM = "internal";

    /**
     * Content-Security-Policy of the {@code /internal/**} chain (REQ-037 AC-20). The status page is
     * pure server-side HTML with inline CSS and no external resource, so everything but inline
     * styles can be denied. / CSP der internen Chain: alles außer Inline-CSS verboten.
     */
    private static final String INTERNAL_CSP = "default-src 'none'; style-src 'self' 'unsafe-inline'; "
        + "img-src 'none'; form-action 'none'; frame-ancestors 'none'; base-uri 'none'";

    private final String frameAncestors;
    private final ErrorReportingProperties reportingProperties;

    public SecurityConfig(
        @Value("${app.frame-ancestors:'self' https://www.gottfried-schultz.de https://gottfried-schultz.de}") String frameAncestors,
        ErrorReportingProperties reportingProperties
    ) {
        // REQ-025: Erlaubt mehrere Hosts (space- oder komma-getrennt). Komma-Listen werden CSP-konform normalisiert.
        this.frameAncestors = frameAncestors == null ? "'self'" : frameAncestors.replace(",", " ").trim();
        this.reportingProperties = reportingProperties;
    }

    /**
     * REQ-030: the internal report page shows customer-data payloads, so /internal/** is
     * protected by HTTP Basic Auth (admin credentials from secrets). Higher precedence than
     * the public catch-all chain below.
     *
     * <p>REQ-037 adds two things to this existing chain: an explicit Basic-Auth realm
     * ({@value #INTERNAL_REALM}, §12.2 — the {@code WWW-Authenticate} value changes from the Spring
     * default {@code Realm}) and the {@code Content-Security-Policy} from AC-20, which this chain
     * did not send at all before (the {@code frame-ancestors} header lives in the {@code @Order(2)}
     * chain and never applied to {@code /internal/**}). The page keeps rendering because it uses
     * inline CSS only, no script and no external resource (AC-3). / REQ-037 ergänzt expliziten
     * Realm und die bisher fehlende CSP; die Seite bleibt vollständig darstellbar.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalReportSecurityChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .httpBasic(basic -> basic.realmName(INTERNAL_REALM))
            .headers(headers -> headers
                .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy", INTERNAL_CSP))
            );
        return http.build();
    }

    /**
     * Logs failed Basic-Auth attempts on the publicly reachable status page as {@code WARN} so
     * brute-force attempts become visible (REQ-037 AC-21). Only the IP is logged, masked via
     * {@link LogSanitizer#maskIp(String)} (/24 resp. /64) — <b>no user name in clear text and no
     * password</b>, and no {@code User-Agent} either (BR-3). / Loggt Fehlversuche mit maskierter IP,
     * ohne Benutzername und ohne Passwort.
     *
     * <p>Spring Boot registers a {@code DefaultAuthenticationEventPublisher}, which also maps
     * "unknown user" to this event — so a wrong user name is logged the same way as a wrong
     * password, without revealing which one it was. / Unbekannter Benutzer und falsches Passwort
     * erzeugen dieselbe Zeile.
     */
    @Bean
    public ApplicationListener<AuthenticationFailureBadCredentialsEvent> internalAuthFailureLogger() {
        return event -> {
            Object details = event.getAuthentication() != null ? event.getAuthentication().getDetails() : null;
            String remoteAddress = details instanceof WebAuthenticationDetails web ? web.getRemoteAddress() : null;
            String maskedIp = remoteAddress != null ? LogSanitizer.maskIp(remoteAddress) : "unknown";
            log.warn("REQ-037: failed authentication attempt on /internal/** from ip={}", maskedIp);
        };
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String csp = "frame-ancestors " + frameAncestors;

        http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())
                .contentTypeOptions(content -> {})
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions.policy("geolocation=(), microphone=(), camera=()"))
            )
            .headers(headers -> headers
                .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy", csp))
                .addHeaderWriter(new StaticHeadersWriter("X-Powered-By", ""))
            );

        return http.build();
    }

    /**
     * Single admin user for the internal report. Password comes from REPORT_ADMIN_PASSWORD.
     * If unset, a random (unknowable) password is used so /internal/** stays locked until
     * the secret is configured.
     */
    @Bean
    public UserDetailsService reportAdminUserDetailsService() {
        ErrorReportingProperties.Admin admin = reportingProperties.admin();
        String user = (admin != null && admin.user() != null && !admin.user().isBlank()) ? admin.user() : "admin";
        String password = admin != null ? admin.password() : null;
        if (password == null || password.isBlank()) {
            password = UUID.randomUUID().toString();
            log.warn("REQ-030: REPORT_ADMIN_PASSWORD not set — /internal/report is locked until configured.");
        }
        UserDetails details = User.withUsername(user).password("{noop}" + password).roles("REPORT").build();
        return new InMemoryUserDetailsManager(details);
    }
}
