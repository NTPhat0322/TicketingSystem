package com.tienphat.presentation.auth;

import com.tienphat.domain.model.UserRole;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class AccessTokenIssuer {

    private static final long ACCESS_TOKEN_TTL_MINUTES = 15;

    private final JwtEncoder jwtEncoder;

    public AccessTokenIssuer(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String issue(UUID userId, UserRole role) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ACCESS_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
