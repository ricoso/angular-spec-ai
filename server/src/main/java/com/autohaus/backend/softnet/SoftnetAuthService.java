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

@Service
public class SoftnetAuthService {

    private static final Logger log = LoggerFactory.getLogger(SoftnetAuthService.class);

    private final SoftnetProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient authClient;

    private String clientEmail;
    private String clientId;
    private RSAPrivateKey privateKey;
    private String privateKeyId;

    private String accessToken = "";
    private long tokenExpiresAt = 0;

    public SoftnetAuthService(SoftnetProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.authClient = RestClient.builder().build();
    }

    @PostConstruct
    public void init() {
        loadFingerprint();
    }

    private void loadFingerprint() {
        String fingerprintPath = properties.fingerprintPath();
        if (fingerprintPath == null || fingerprintPath.isBlank()) {
            log.warn("SOFTNET_FINGERPRINT_PATH not set — SoftNet auth will fail");
            return;
        }

        Path absolutePath = Path.of(fingerprintPath).toAbsolutePath();
        try {
            String content = Files.readString(absolutePath);
            JsonNode node = objectMapper.readTree(content);
            this.clientEmail = node.get("client_email").asText();
            this.clientId = node.get("client_id").asText();
            this.privateKeyId = node.get("private_key_id").asText();

            String pemKey = node.get("private_key").asText();
            this.privateKey = parseRsaPrivateKey(pemKey);

            log.info("Fingerprint loaded from {}", absolutePath);
        } catch (Exception e) {
            log.error("Failed to load fingerprint from {}: {}", absolutePath, e.getMessage());
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
            // DER sequence: SEQUENCE { version, algorithm, privateKey }
            byte[] pkcs8Bytes = wrapPkcs1InPkcs8(keyBytes);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8Bytes);
            return (RSAPrivateKey) factory.generatePrivate(spec);
        } else {
            // PKCS#8 format: use directly
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return (RSAPrivateKey) factory.generatePrivate(spec);
        }
    }

    private byte[] wrapPkcs1InPkcs8(byte[] pkcs1Bytes) {
        // RSA algorithm OID: 1.2.840.113549.1.1.1
        byte[] rsaOid = {0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
            (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01};
        byte[] nullParam = {0x05, 0x00};

        // AlgorithmIdentifier SEQUENCE
        byte[] algId = encodeSequence(concat(rsaOid, nullParam));

        // Version INTEGER 0
        byte[] version = {0x02, 0x01, 0x00};

        // PrivateKey OCTET STRING wrapping PKCS#1 key
        byte[] privateKeyOctet = encodeOctetString(pkcs1Bytes);

        // Outer SEQUENCE
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

    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis() / 1000;
        if (!accessToken.isEmpty() && tokenExpiresAt > now + 60) {
            return accessToken;
        }

        log.info("Requesting new SoftNet access token...");
        try {
            long iat = now;
            long exp = iat + 300;

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("id", privateKeyId)
                .claim("scope", "scope.api.planning.service")
                .subject(clientEmail)
                .issuer(clientId)
                .audience(properties.authUrl())
                .issueTime(new Date(iat * 1000))
                .expirationTime(new Date(exp * 1000))
                .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            JWSSigner signer = new RSASSASigner(privateKey);
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
            this.accessToken = responseJson.get("access_token").asText();
            int expiresIn = responseJson.get("expires_in").asInt();
            this.tokenExpiresAt = now + expiresIn;

            log.info("SoftNet token obtained, expires in {}s", expiresIn);
            return this.accessToken;
        } catch (Exception e) {
            log.error("Failed to get SoftNet access token: {}", e.getMessage());
            throw new RuntimeException("Failed to authenticate with SoftNet API", e);
        }
    }
}
