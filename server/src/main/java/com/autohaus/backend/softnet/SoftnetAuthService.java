package com.autohaus.backend.softnet;

import com.autohaus.backend.config.SoftnetProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * SoftNet JWT-bearer authentication.
 *
 * <p>REQ-041: holds TWO credentials with separate token caches so that recall-campaign bookings
 * are billed on their own SoftNet client ("Abrechnung"): the <b>default</b> fingerprint serves the
 * normal OTV (default + tireChange groups), the <b>recall</b> fingerprint serves the recall group
 * ({@code softnet.recall-group-id}). {@link #getAccessToken(String)} picks the credential by group.
 * If no recall fingerprint is configured, the recall group falls back to the default credential so
 * environments without the dedicated recall client (e.g. INT before provisioning) keep working.
 */
@Service
public class SoftnetAuthService {

    private static final Logger log = LoggerFactory.getLogger(SoftnetAuthService.class);

    private final SoftnetProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient authClient;

    private final Credential defaultCredential = new Credential("default");
    private final Credential recallCredential = new Credential("recall");
    private String recallGroupId;

    public SoftnetAuthService(SoftnetProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.authClient = RestClient.builder().build();
    }

    @PostConstruct
    public void init() {
        this.recallGroupId = properties.recallGroupId();
        loadCredential(defaultCredential, properties.fingerprintJson(), properties.fingerprintPath());
        loadCredential(recallCredential, properties.recallFingerprintJson(), properties.recallFingerprintPath());
        if (!recallCredential.loaded) {
            log.info("No recall fingerprint configured — recall group falls back to the default credential");
        }
    }

    /** Backwards-compatible: the default credential's token (normal OTV + tireChange). */
    public String getAccessToken() {
        return getAccessToken(null);
    }

    /**
     * REQ-041: token for the given SoftNet group. The recall group uses the recall credential (when
     * configured); every other group — and the recall group without a dedicated credential — uses
     * the default credential.
     */
    public String getAccessToken(String groupId) {
        boolean isRecall = recallGroupId != null && !recallGroupId.isBlank()
            && recallGroupId.equals(groupId) && recallCredential.loaded;
        return tokenFor(isRecall ? recallCredential : defaultCredential);
    }

    private void loadCredential(Credential credential, String fingerprintJson, String fingerprintPath) {
        String json = fingerprintJson;

        // Fall back to file if the env var is empty.
        if ((json == null || json.isBlank()) && fingerprintPath != null && !fingerprintPath.isBlank()) {
            try {
                Path path = Path.of(fingerprintPath);
                if (Files.exists(path)) {
                    json = Files.readString(path);
                    log.info("[{}] Fingerprint loaded from file: {}", credential.name, fingerprintPath);
                } else {
                    log.warn("[{}] fingerprint path set but file not found: {}", credential.name, fingerprintPath);
                }
            } catch (Exception e) {
                log.error("[{}] failed to read fingerprint from file {}: {}", credential.name, fingerprintPath, e.getMessage());
            }
        }

        if (json == null || json.isBlank()) {
            if ("default".equals(credential.name)) {
                log.warn("SOFTNET_FINGERPRINT_JSON not set and no fingerprint file available — SoftNet auth will fail");
            }
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            credential.clientEmail = node.get("client_email").asText();
            credential.clientId = node.get("client_id").asText();
            credential.privateKeyId = node.get("private_key_id").asText();
            credential.privateKey = parseRsaPrivateKey(node.get("private_key").asText());
            credential.loaded = true;
            log.info("[{}] Fingerprint parsed successfully — SoftNet auth ready", credential.name);
        } catch (Exception e) {
            log.error("[{}] failed to parse fingerprint JSON: {}", credential.name, e.getMessage());
        }
    }

    private synchronized String tokenFor(Credential credential) {
        if (!credential.loaded) {
            throw new IllegalStateException(
                "Failed to authenticate with SoftNet API — credential '" + credential.name + "' is not configured");
        }
        long now = System.currentTimeMillis() / 1000;
        if (!credential.accessToken.isEmpty() && credential.expiresAt > now + 60) {
            return credential.accessToken;
        }

        log.info("[{}] Requesting new SoftNet access token...", credential.name);
        try {
            long iat = now;
            long exp = iat + 300;

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("id", credential.privateKeyId)
                .claim("scope", "scope.api.planning.service")
                .subject(credential.clientEmail)
                .issuer(credential.clientId)
                .audience(properties.authUrl())
                .issueTime(new Date(iat * 1000))
                .expirationTime(new Date(exp * 1000))
                .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            JWSSigner signer = new RSASSASigner(credential.privateKey);
            signedJWT.sign(signer);
            String jwtString = signedJWT.serialize();

            String body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + jwtString;

            String responseBody = authClient.post()
                .uri(properties.authUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode responseJson = objectMapper.readTree(responseBody);
            credential.accessToken = responseJson.get("access_token").asText();
            int expiresIn = responseJson.get("expires_in").asInt();
            credential.expiresAt = now + expiresIn;

            log.info("[{}] SoftNet token obtained, expires in {}s", credential.name, expiresIn);
            return credential.accessToken;
        } catch (Exception e) {
            log.error("[{}] failed to get SoftNet access token: {}", credential.name, e.getMessage());
            throw new RuntimeException("Failed to authenticate with SoftNet API", e);
        }
    }

    /** One SoftNet client credential plus its token cache. */
    private static final class Credential {
        private final String name;
        private String clientEmail;
        private String clientId;
        private String privateKeyId;
        private RSAPrivateKey privateKey;
        private boolean loaded;
        private String accessToken = "";
        private long expiresAt = 0;

        private Credential(String name) {
            this.name = name;
        }
    }

    private RSAPrivateKey parseRsaPrivateKey(String pem) throws Exception {
        boolean isPkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");

        String stripped = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        KeyFactory factory = KeyFactory.getInstance("RSA");

        if (isPkcs1) {
            // PKCS#1 format: wrap in PKCS#8 envelope
            byte[] pkcs8Bytes = wrapPkcs1InPkcs8(keyBytes);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8Bytes);
            return (RSAPrivateKey) factory.generatePrivate(spec);
        } else {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return (RSAPrivateKey) factory.generatePrivate(spec);
        }
    }

    private byte[] wrapPkcs1InPkcs8(byte[] pkcs1Bytes) {
        // RSA algorithm OID: 1.2.840.113549.1.1.1
        byte[] rsaOid = {0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
            (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01};
        byte[] nullParam = {0x05, 0x00};

        byte[] algId = encodeSequence(concat(rsaOid, nullParam));
        byte[] version = {0x02, 0x01, 0x00};
        byte[] privateKeyOctet = encodeOctetString(pkcs1Bytes);

        return encodeSequence(concat(concat(version, algId), privateKeyOctet));
    }

    private byte[] encodeSequence(byte[] content) {
        return encodeTlv((byte) 0x30, content);
    }

    private byte[] encodeOctetString(byte[] content) {
        return encodeTlv((byte) 0x04, content);
    }

    private byte[] encodeTlv(byte tag, byte[] content) {
        byte[] length = encodeLength(content.length);
        byte[] result = new byte[1 + length.length + content.length];
        result[0] = tag;
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(content, 0, result, 1 + length.length, content.length);
        return result;
    }

    private byte[] encodeLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        } else if (length < 256) {
            return new byte[]{(byte) 0x81, (byte) length};
        } else {
            return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) (length & 0xff)};
        }
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
