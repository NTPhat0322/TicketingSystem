package com.tienphat.presentation.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SecurityConfigTest.TestApplication.class)
class SecurityConfigTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void securityBeansAreWiredWithAWorkingJwtRoundTrip() {
        UUID userId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("role", "CUSTOMER")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(60))
                .build();

        Jwt decoded = jwtDecoder.decode(jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue());

        assertThat(passwordEncoder).isInstanceOf(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class);
        assertThat(jwtEncoder).isNotNull();
        assertThat(jwtDecoder).isNotNull();
        assertThat(jwtAuthenticationConverter).isNotNull();
        assertThat(securityFilterChain).isNotNull();
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("role")).isEqualTo("CUSTOMER");
    }

    @Test
    void jwtAuthenticationConverterMapsRoleAndSubject() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("role", "ADMIN")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();

        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication.getName()).isEqualTo(userId.toString());
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN")
                .doesNotContain("ROLE_CUSTOMER", "ROLE_ORGANIZER");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SecurityConfig.class)
    static class TestApplication {
    }
}
