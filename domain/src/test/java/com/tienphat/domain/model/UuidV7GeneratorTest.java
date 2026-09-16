package com.tienphat.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    @Test
    @DisplayName("generate() sets the version nibble to 7 and the RFC 9562 variant bits")
    void generate_setsVersionAndVariantBits() {
        for (int i = 0; i < 100; i++) {
            UUID id = UuidV7Generator.generate();

            assertThat(id.version()).as("version nibble").isEqualTo(7);
            assertThat(id.variant()).as("variant bits (2 == RFC 4122/9562)").isEqualTo(2);
        }
    }

    @Test
    @DisplayName("the embedded timestamp is the current unix-millisecond clock")
    void generate_embedsCurrentUnixMillis() {
        long before = System.currentTimeMillis();
        UUID id = UuidV7Generator.generate();
        long after = System.currentTimeMillis();

        assertThat(UuidV7Generator.timestampOf(id)).isBetween(before, after);
    }

    @Test
    @DisplayName("successive ids are non-decreasing by timestamp — sortable, not strictly monotonic")
    void generate_isTimeOrdered() {
        List<Long> timestamps = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            timestamps.add(UuidV7Generator.timestampOf(UuidV7Generator.generate()));
        }

        assertThat(timestamps).isSorted();
    }

    @Test
    @DisplayName("ids minted in the same millisecond are still distinct — 74 random bits per id")
    void generate_isUniqueWithinOneMillisecond() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UuidV7Generator.generate());
        }

        assertThat(ids).hasSize(10_000);
    }

    @Test
    @DisplayName("the random half is not fixed — two ids differ below the timestamp prefix")
    void generate_randomBitsVary() {
        UUID first = UuidV7Generator.generate();
        UUID second = UuidV7Generator.generate();

        assertThat(second.getLeastSignificantBits()).isNotEqualTo(first.getLeastSignificantBits());
    }
}
