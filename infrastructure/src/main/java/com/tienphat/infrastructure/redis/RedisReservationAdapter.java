package com.tienphat.infrastructure.redis;

import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.port.ReservationRequest;
import com.tienphat.domain.port.ReservationResult;
import com.tienphat.domain.port.StockCachePort;
import com.tienphat.domain.vo.Money;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis implementation of the live inventory and recoverable reservation-intent ports.
 *
 * <p>The reservation and release boundaries are Lua scripts because stock, the per-user counter,
 * the hold, and the recovery index must move together. The Java methods only translate the domain
 * snapshot to stable Redis strings and map the script result back to the domain vocabulary.
 */
@Component
public class RedisReservationAdapter implements StockCachePort, ReservationIntentStore {

    private static final int RESERVE_SUCCESS = 0;
    private static final int RESERVE_OUT_OF_STOCK = 1;
    private static final int RESERVE_USER_LIMIT = 2;
    private static final int RESERVE_STOCK_NOT_WARMED = 3;
    private static final int RESERVE_DUPLICATE_ORDER = 4;

    private static final long RELEASE_SUCCESS = 1L;
    private static final long RELEASE_ALREADY_RELEASED = 2L;

    private static final int ACTION_RESCHEDULE = 1;
    private static final int ACTION_ENQUEUED = 2;
    private static final int ACTION_ORDER_CREATED = 3;
    private static final int ACTION_EXPIRED = 4;
    private static final int ACTION_COMPLETED = 5;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> reserveScript;
    private final RedisScript<Long> releaseScript;
    private final RedisScript<Long> stateScript;
    private final RedisScript<Long> releaseClaimScript;
    private final HashOperations<String, String, String> hashes;

    public RedisReservationAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = script("scripts/reservation-reserve.lua");
        this.releaseScript = script("scripts/reservation-release.lua");
        this.stateScript = script("scripts/reservation-state.lua");
        this.releaseClaimScript = script("scripts/reservation-release-claim.lua");
        this.hashes = redisTemplate.opsForHash();
    }

    @Override
    public ReservationResult tryReserve(ReservationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Reservation request must not be null");
        }

        long holdTtlMillis = Math.max(1L,
                Duration.between(request.reservedAt(), request.expiresAt()).toMillis());
        List<String> keys = List.of(
                RedisReservationKeys.stock(request.ticketTypeId()),
                RedisReservationKeys.userLimit(request.ticketTypeId(), request.userId()),
                RedisReservationKeys.hold(request.orderId()),
                RedisReservationKeys.intent(request.orderId()),
                RedisReservationKeys.PENDING_INTENTS);
        List<String> arguments = List.of(
                request.orderId().toString(),
                request.userId().toString(),
                request.eventId().toString(),
                request.ticketTypeId().toString(),
                Integer.toString(request.quantity()),
                request.unitPrice().getAmount().toPlainString(),
                Integer.toString(request.maxPerUser()),
                Integer.toString(request.holdDurationSec()),
                request.reservedAt().toString(),
                request.expiresAt().toString(),
                request.reservedAt().toString(),
                Long.toString(holdTtlMillis),
                Long.toString(request.reservedAt().toEpochMilli()));

        Long result = execute(reserveScript, keys, arguments);
        int resultCode = Math.toIntExact(requireResult(result, "reserve"));
        return switch (resultCode) {
            case RESERVE_SUCCESS -> ReservationResult.SUCCESS;
            case RESERVE_OUT_OF_STOCK -> ReservationResult.OUT_OF_STOCK;
            case RESERVE_USER_LIMIT -> ReservationResult.USER_LIMIT_EXCEEDED;
            case RESERVE_STOCK_NOT_WARMED -> ReservationResult.STOCK_NOT_WARMED;
            case RESERVE_DUPLICATE_ORDER -> throw new IllegalStateException(
                    "Reservation order already exists: " + request.orderId());
            default -> throw new IllegalStateException("Unknown Redis reserve result: " + resultCode);
        };
    }

    @Override
    public void release(UUID orderId, UUID ticketTypeId, UUID userId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive");
        }

        List<String> keys = List.of(
                RedisReservationKeys.hold(orderId),
                RedisReservationKeys.intent(orderId),
                RedisReservationKeys.stock(ticketTypeId),
                RedisReservationKeys.userLimit(ticketTypeId, userId),
                RedisReservationKeys.PENDING_INTENTS);
        List<String> arguments = List.of(
                requireId(orderId, "orderId").toString(),
                requireId(ticketTypeId, "ticketTypeId").toString(),
                requireId(userId, "userId").toString(),
                Integer.toString(quantity));

        long result = requireResult(execute(releaseScript, keys, arguments), "release");
        if (result == RELEASE_SUCCESS || result == RELEASE_ALREADY_RELEASED) {
            return;
        }
        if (result == 3L) {
            throw new IllegalStateException("Reservation intent does not exist: " + orderId);
        }
        if (result == 4L) {
            throw new IllegalStateException("Reservation release metadata mismatch: " + orderId);
        }
        if (result == 5L) {
            throw new IllegalStateException("Reservation user counter is smaller than release quantity: "
                    + orderId);
        }
        if (result == 6L) {
            throw new IllegalStateException("Reservation stock key is missing during release: " + ticketTypeId);
        }
        throw new IllegalStateException("Unknown Redis release result: " + result);
    }

    @Override
    public int getAvailableStock(UUID ticketTypeId) {
        String value = redisTemplate.opsForValue().get(RedisReservationKeys.stock(ticketTypeId));
        if (value == null) {
            return 0;
        }
        return parseInt(value, "stock");
    }

    @Override
    public void warmUp(UUID ticketTypeId, int totalQuantity) {
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("totalQuantity must not be negative");
        }
        String key = RedisReservationKeys.stock(ticketTypeId);
        boolean created = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, Integer.toString(totalQuantity)));
        if (created) {
            return;
        }

        String existing = redisTemplate.opsForValue().get(key);
        if (existing == null || parseInt(existing, "stock") != totalQuantity) {
            throw new IllegalStateException(
                    "Refusing to overwrite live stock for ticket type " + ticketTypeId);
        }
    }

    @Override
    public Optional<ReservationIntent> findByOrderId(UUID orderId) {
        Map<String, String> fields = hashes.entries(RedisReservationKeys.intent(orderId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toIntent(fields, orderId));
    }

    @Override
    public List<ReservationIntent> findDue(Instant now, int limit) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        Set<String> dueOrderIds = redisTemplate.opsForZSet().rangeByScore(
                RedisReservationKeys.PENDING_INTENTS,
                Double.NEGATIVE_INFINITY,
                now.toEpochMilli(),
                0,
                limit);
        if (dueOrderIds == null || dueOrderIds.isEmpty()) {
            return List.of();
        }

        List<ReservationIntent> due = new ArrayList<>(dueOrderIds.size());
        for (String rawOrderId : dueOrderIds) {
            UUID orderId = UUID.fromString(rawOrderId);
            Optional<ReservationIntent> intent = findByOrderId(orderId);
            if (intent.isPresent() && intent.get().state() == ReservationIntentState.PENDING) {
                due.add(intent.get());
            }
        }
        return List.copyOf(due);
    }

    @Override
    public List<ReservationIntent> findStaleEnqueued(Instant now, Duration gracePeriod, int limit) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (gracePeriod == null || gracePeriod.isNegative()) {
            throw new IllegalArgumentException("gracePeriod must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        Instant staleBefore = now.minus(gracePeriod);
        return scanIntents(limit, intent -> intent.state() == ReservationIntentState.ENQUEUED
                && !referenceTime(intent).isAfter(staleBefore));
    }

    @Override
    public List<ReservationIntent> findExpired(Instant now, int limit) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        return scanIntents(limit, intent -> intent.state() != ReservationIntentState.RELEASED
                && intent.state() != ReservationIntentState.COMPLETED
                && !intent.expiresAt().isAfter(now));
    }

    @Override
    public boolean tryClaim(UUID orderId, String owner, Duration lease) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Claim owner must not be blank");
        }
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("Claim lease must be positive");
        }
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                RedisReservationKeys.claim(orderId), owner, lease);
        return Boolean.TRUE.equals(claimed);
    }

    @Override
    public void releaseClaim(UUID orderId, String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Claim owner must not be blank");
        }
        execute(releaseClaimScript,
                List.of(RedisReservationKeys.claim(orderId)),
                List.of(owner));
    }

    @Override
    public void reschedule(UUID orderId, int retryCount, Instant nextRetryAt) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (nextRetryAt == null) {
            throw new IllegalArgumentException("nextRetryAt must not be null");
        }
        executeState(orderId, ACTION_RESCHEDULE, retryCount, nextRetryAt);
    }

    @Override
    public void markEnqueued(UUID orderId) {
        executeState(orderId, ACTION_ENQUEUED, 0, Instant.now());
    }

    @Override
    public void markOrderCreated(UUID orderId) {
        executeState(orderId, ACTION_ORDER_CREATED, 0, Instant.EPOCH);
    }

    @Override
    public void markExpired(UUID orderId) {
        executeState(orderId, ACTION_EXPIRED, 0, Instant.EPOCH);
    }

    @Override
    public void markCompleted(UUID orderId) {
        executeState(orderId, ACTION_COMPLETED, 0, Instant.EPOCH);
    }

    private void executeState(UUID orderId, int action, int retryCount, Instant nextRetryAt) {
        List<String> keys = action == ACTION_COMPLETED
                ? List.of(
                        RedisReservationKeys.intent(orderId),
                        RedisReservationKeys.PENDING_INTENTS,
                        RedisReservationKeys.hold(orderId))
                : List.of(RedisReservationKeys.intent(orderId), RedisReservationKeys.PENDING_INTENTS);
        execute(stateScript,
                keys,
                List.of(Integer.toString(action), Integer.toString(retryCount),
                        nextRetryAt.toString(), Long.toString(nextRetryAt.toEpochMilli())));
    }

    private ReservationIntent toIntent(Map<String, String> fields, UUID requestedOrderId) {
        UUID orderId = UUID.fromString(required(fields, RedisReservationFields.ORDER_ID));
        if (!orderId.equals(requestedOrderId)) {
            throw new IllegalStateException("Reservation intent key/id mismatch: " + requestedOrderId);
        }
        return new ReservationIntent(
                orderId,
                UUID.fromString(required(fields, RedisReservationFields.USER_ID)),
                UUID.fromString(required(fields, RedisReservationFields.EVENT_ID)),
                UUID.fromString(required(fields, RedisReservationFields.TICKET_TYPE_ID)),
                parseInt(required(fields, RedisReservationFields.QUANTITY), "quantity"),
                Money.of(new BigDecimal(required(fields, RedisReservationFields.UNIT_PRICE))),
                parseInt(required(fields, RedisReservationFields.MAX_PER_USER), "maxPerUser"),
                parseInt(required(fields, RedisReservationFields.HOLD_DURATION_SEC), "holdDurationSec"),
                parseInstant(fields, RedisReservationFields.RESERVED_AT),
                parseInstant(fields, RedisReservationFields.EXPIRES_AT),
                ReservationIntentState.valueOf(required(fields, RedisReservationFields.STATE)),
                parseInt(required(fields, RedisReservationFields.RETRY_COUNT), "retryCount"),
                parseInstant(fields, RedisReservationFields.NEXT_RETRY_AT),
                parseOptionalInstant(fields, RedisReservationFields.ENQUEUED_AT));
    }

    private static Instant parseInstant(Map<String, String> fields, String fieldName) {
        return Instant.parse(required(fields, fieldName));
    }

    private static Instant parseOptionalInstant(Map<String, String> fields, String fieldName) {
        String value = fields.get(fieldName);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private List<ReservationIntent> scanIntents(
            int limit,
            java.util.function.Predicate<ReservationIntent> predicate) {
        List<ReservationIntent> matches = new ArrayList<>(limit);
        ScanOptions options = ScanOptions.scanOptions()
                .match("reservation:intent:*")
                .count(Math.max(100, limit * 4))
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext() && matches.size() < limit) {
                String key = cursor.next();
                String rawOrderId = key.substring("reservation:intent:".length());
                try {
                    UUID orderId = UUID.fromString(rawOrderId);
                    Optional<ReservationIntent> intent = findByOrderId(orderId);
                    if (intent.isPresent() && predicate.test(intent.get())) {
                        matches.add(intent.get());
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore unrelated keys that happen to share the configured prefix.
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to scan Redis reservation intents", exception);
        }
        return List.copyOf(matches);
    }

    private static Instant referenceTime(ReservationIntent intent) {
        return intent.enqueuedAt() == null ? intent.nextRetryAt() : intent.enqueuedAt();
    }

    private static String required(Map<String, String> fields, String fieldName) {
        String value = fields.get(fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Reservation intent field is missing: " + fieldName);
        }
        return value;
    }

    private static int parseInt(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid Redis " + fieldName + " value: " + value, exception);
        }
    }

    private static UUID requireId(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static RedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        try {
            script.afterPropertiesSet();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load Redis script " + path, exception);
        }
        return script;
    }

    private <T> T execute(RedisScript<T> script, List<String> keys, List<String> arguments) {
        T result = redisTemplate.execute(script, keys, arguments.toArray());
        if (result == null) {
            throw new IllegalStateException("Redis Lua script returned no result");
        }
        return result;
    }

    private static long requireResult(Long result, String operation) {
        if (result == null) {
            throw new IllegalStateException("Redis " + operation + " script returned no result");
        }
        return result;
    }
}
