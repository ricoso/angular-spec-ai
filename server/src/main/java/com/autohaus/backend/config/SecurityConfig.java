package com.autohaus.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

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
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalReportSecurityChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
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
