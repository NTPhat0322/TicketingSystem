package com.tienphat.presentation.auth;

import com.tienphat.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenIssuerTest {

    private static final String SECRET = "phase3-test-secret-key-must-be-at-least-32-bytes-long";

    private AccessTokenIssuer accessTokenIssuer;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        accessTokenIssuer = new AccessTokenIssuer(jwtEncoder);
    }

    @Test
    void issue_createsAnHs256TokenWithTheExpectedIdentityAndLifetime() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = jwtDecoder.decode(accessTokenIssuer.issue(userId, UserRole.ADMIN));

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds())
                .isBetween(898L, 902L);
    }
}
