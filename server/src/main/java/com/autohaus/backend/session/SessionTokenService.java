package com.autohaus.backend.session;

import com.autohaus.backend.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class SessionTokenService {

    private final String secret;
    private final int ttlMinutes;

    public SessionTokenService(AppProperties appProperties) {
        this.secret = appProperties.session().secret();
        this.ttlMinutes = appProperties.session().ttlMinutes();
    }

    public TokenResult generateToken() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String hmac = createHmac(timestamp);
        String token = toBase64Url(timestamp) + "." + toBase64Url(hmac);
        String expiresAt = Instant.now().plusSeconds(ttlMinutes * 60L).toString();
        return new TokenResult(token, expiresAt);
    }

    public boolean validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) return false;

            String timestamp = fromBase64Url(parts[0]);
            String providedHmac = fromBase64Url(parts[1]);
            String expectedHmac = createHmac(timestamp);

            if (!java.security.MessageDigest.isEqual(
                providedHmac.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedHmac.getBytes(java.nio.charset.StandardCharsets.UTF_8))) return false;

            long tokenTime = Long.parseLong(timestamp);
            long now = System.currentTimeMillis();
            long maxAge = ttlMinutes * 60L * 1000L;

            return (now - tokenTime) <= maxAge;
        } catch (Exception e) {
            return false;
        }
    }

    private String createHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HMAC", e);
        }
    }

    private String toBase64Url(String str) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    private String fromBase64Url(String str) {
        return new String(Base64.getUrlDecoder().decode(str), StandardCharsets.UTF_8);
    }

    public record TokenResult(String token, String expiresAt) {}
}
