package com.tienphat.infrastructure.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Infra-only persistence record for a refresh token — no domain port wraps this (see plan.md
 * Research Summary #4); {@code application.auth.RefreshTokenStore} is the abstraction layer above it.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenJpaEntity {

    @Id
    private UUID id;

    private UUID userId;

    /** SHA-256 hash of the raw refresh token — the raw value itself is never persisted. */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt;
}
