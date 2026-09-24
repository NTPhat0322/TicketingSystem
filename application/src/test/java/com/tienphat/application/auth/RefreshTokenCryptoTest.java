package com.tienphat.application.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCryptoTest {

    @Test
    @DisplayName("hash() is deterministic for the same input")
    void hash_isDeterministic() {
        String raw = RefreshTokenCrypto.generateRawToken();

        assertThat(RefreshTokenCrypto.hash(raw)).isEqualTo(RefreshTokenCrypto.hash(raw));
    }

    @Test
    @DisplayName("hash() produces different output for different input")
    void hash_differsForDifferentInput() {
        String hashA = RefreshTokenCrypto.hash("token-a");
        String hashB = RefreshTokenCrypto.hash("token-b");

        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    @DisplayName("hash() never equals its own input")
    void hash_neverEqualsInput() {
        String raw = RefreshTokenCrypto.generateRawToken();

        assertThat(RefreshTokenCrypto.hash(raw)).isNotEqualTo(raw);
    }

    @Test
    @DisplayName("generateRawToken() produces 100 distinct values")
    void generateRawToken_producesDistinctValues() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(RefreshTokenCrypto.generateRawToken());
        }

        assertThat(tokens).hasSize(100);
    }
}
