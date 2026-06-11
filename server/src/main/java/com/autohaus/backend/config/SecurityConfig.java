package com.autohaus.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String frameAncestors;

    public SecurityConfig(
        @Value("${app.frame-ancestors:'self' https://www.gottfried-schultz.de https://gottfried-schultz.de}") String frameAncestors
    ) {
        // REQ-025: Erlaubt mehrere Hosts (space- oder komma-getrennt). Beispiel via Env-Var:
        //   APP_FRAME_ANCESTORS="'self' https://www.gottfried-schultz.de https://kunde-typo3.de https://*.kundedomain.de"
        // Komma-getrennte Listen werden in Spaces normalisiert (CSP-konform).
        this.frameAncestors = frameAncestors == null ? "'self'" : frameAncestors.replace(",", " ").trim();
    }

    @Bean
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
}
