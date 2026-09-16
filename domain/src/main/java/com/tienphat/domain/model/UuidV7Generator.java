package com.tienphat.domain.model;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates RFC 9562 version-7 UUIDs: a 48-bit unix-millisecond prefix followed by random bits.
 *
 * <p>Package-private on purpose. Design doc §2.1 puts {@code Order.id} generation in application
 * code rather than in the database, and {@link Order#create} is the only caller; exposing this
 * would invite other layers to mint order ids of their own.
 *
 * <p>Hand-rolled rather than pulled from a library because {@code domain} depends on nothing but
 * Lombok, and the algorithm is a bit-layout, not business logic. {@code UuidV7GeneratorTest} pins
 * the version nibble, the variant bits and the time-ordering property.
 *
 * <p>Ordering is by millisecond, not by call. Two ids minted inside the same millisecond sort
 * arbitrarily against each other — enough for index locality, not a sequence number.
 */
final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7Generator() {
    }

    /**
     * Layout (RFC 9562 §5.7):
     * <pre>
     * msb: | unix_ts_ms (48) | ver = 7 (4) | rand_a (12) |
     * lsb: | var = 0b10 (2)  | rand_b (62)               |
     * </pre>
     */
    static UUID generate() {
        long unixTsMs = System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL;

        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        long randA = ((random[0] & 0xFFL) << 4) | ((random[1] & 0xF0L) >>> 4);
        long msb = (unixTsMs << 16) | (0x7L << 12) | randA;

        long randB = 0L;
        for (int i = 2; i < random.length; i++) {
            randB = (randB << 8) | (random[i] & 0xFFL);
        }
        long lsb = (randB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;

        return new UUID(msb, lsb);
    }

    /** The embedded unix-millisecond timestamp. Test-facing: the ordering property is what matters. */
    static long timestampOf(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
