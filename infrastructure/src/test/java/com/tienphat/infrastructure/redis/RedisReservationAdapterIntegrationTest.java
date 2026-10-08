package com.tienphat.infrastructure.redis;

import com.tienphat.domain.model.Order;
import com.tienphat.domain.port.ReservationIntent;
import com.tienphat.domain.port.ReservationIntentState;
import com.tienphat.domain.port.ReservationResult;
import com.tienphat.domain.port.ReservationRequest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import com.tienphat.infrastructure.messaging.AbstractRedisRabbitIntegrationTest;
import com.tienphat.domain.vo.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class RedisReservationAdapterIntegrationTest extends AbstractRedisRabbitIntegrationTest {

    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Autowired
    private RedisReservationAdapter reservationAdapter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @AfterEach
    void clearRedisAfterTest() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void reservationAtomicallyStoresHoldIntentAndRetryIndex() {
        reservationAdapter.warmUp(TICKET_TYPE_ID, 5);
        ReservationRequest request = request(2, 5, Duration.ofMinutes(1), UUID.randomUUID());

        assertThat(reservationAdapter.tryReserve(request)).isEqualTo(ReservationResult.SUCCESS);
        assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(3);
        assertThat(redisTemplate.opsForValue().get(userLimitKey(request))).isEqualTo("2");
        assertThat(redisTemplate.getExpire(holdKey(request), TimeUnit.MILLISECONDS)).isPositive();
        assertThat(redisTemplate.opsForZSet().score(
                RedisReservationKeys.PENDING_INTENTS, request.orderId().toString())).isEqualTo(
                (double) request.reservedAt().toEpochMilli());

        ReservationIntent intent = reservationAdapter.findByOrderId(request.orderId()).orElseThrow();
        assertThat(intent).usingRecursiveComparison().isEqualTo(new ReservationIntent(
                request.orderId(), request.userId(), request.eventId(), request.ticketTypeId(),
                request.quantity(), request.unitPrice(), request.maxPerUser(), request.holdDurationSec(),
                request.reservedAt(), request.expiresAt(), ReservationIntentState.PENDING, 0,
                request.reservedAt()));
        assertThat(reservationAdapter.findDue(Instant.now().plusSeconds(1), 10))
                .extracting(ReservationIntent::orderId)
                .containsExactly(request.orderId());
    }

    @Test
    void missingStockFailsClosedWithoutCreatingAnyReservationState() {
        ReservationRequest request = request(1, 5, Duration.ofMinutes(1), UUID.randomUUID());

        assertThat(reservationAdapter.tryReserve(request)).isEqualTo(ReservationResult.STOCK_NOT_WARMED);
        assertThat(redisTemplate.opsForValue().get(userLimitKey(request))).isNull();
        assertThat(reservationAdapter.findByOrderId(request.orderId())).isEmpty();
        assertThat(redisTemplate.opsForZSet().score(
                RedisReservationKeys.PENDING_INTENTS, request.orderId().toString())).isNull();
    }

    @Test
    void rejectedStockAndUserLimitAttemptsLeaveNoPartialCountersOrIntent() {
        reservationAdapter.warmUp(TICKET_TYPE_ID, 2);
        ReservationRequest tooMuchStock = request(3, 5, Duration.ofMinutes(1), UUID.randomUUID());

        assertThat(reservationAdapter.tryReserve(tooMuchStock)).isEqualTo(ReservationResult.OUT_OF_STOCK);
        assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(2);
        assertThat(redisTemplate.opsForValue().get(userLimitKey(tooMuchStock))).isNull();
        assertThat(reservationAdapter.findByOrderId(tooMuchStock.orderId())).isEmpty();

        ReservationRequest first = request(1, 1, Duration.ofMinutes(1), tooMuchStock.userId());
        assertThat(reservationAdapter.tryReserve(first)).isEqualTo(ReservationResult.SUCCESS);
        ReservationRequest overLimit = request(1, 1, Duration.ofMinutes(1), first.userId());
        assertThat(reservationAdapter.tryReserve(overLimit)).isEqualTo(ReservationResult.USER_LIMIT_EXCEEDED);
        assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(userLimitKey(overLimit))).isEqualTo("1");
        assertThat(reservationAdapter.findByOrderId(overLimit.orderId())).isEmpty();
    }

    @Test
    void releaseRestoresCountersOnlyOnceAndKeepsIntentAuditRecord() {
        reservationAdapter.warmUp(TICKET_TYPE_ID, 5);
        ReservationRequest request = request(2, 5, Duration.ofMinutes(1), UUID.randomUUID());
        assertThat(reservationAdapter.tryReserve(request)).isEqualTo(ReservationResult.SUCCESS);

        reservationAdapter.release(request.orderId(), request.ticketTypeId(), request.userId(), request.quantity());
        assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(5);
        assertThat(redisTemplate.opsForValue().get(userLimitKey(request))).isEqualTo("0");
        assertThat(redisTemplate.opsForValue().get(holdKey(request))).isNull();
        assertThat(redisTemplate.opsForZSet().score(
                RedisReservationKeys.PENDING_INTENTS, request.orderId().toString())).isNull();
        assertThat(reservationAdapter.findByOrderId(request.orderId()).orElseThrow().state())
                .isEqualTo(ReservationIntentState.RELEASED);

        reservationAdapter.release(request.orderId(), request.ticketTypeId(), request.userId(), request.quantity());
        assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(5);
        assertThat(redisTemplate.opsForValue().get(userLimitKey(request))).isEqualTo("0");
    }

    @Test
    void intentSurvivesShortHoldExpiry() {
        reservationAdapter.warmUp(TICKET_TYPE_ID, 2);
        ReservationRequest request = request(1, 2, Duration.ofSeconds(1), UUID.randomUUID());
        assertThat(reservationAdapter.tryReserve(request)).isEqualTo(ReservationResult.SUCCESS);

        await().atMost(Duration.ofSeconds(4)).untilAsserted(() ->
                assertThat(redisTemplate.opsForHash().entries(holdKey(request))).isEmpty());
        assertThat(reservationAdapter.findByOrderId(request.orderId())).isPresent();
        assertThat(reservationAdapter.findByOrderId(request.orderId()).orElseThrow().state())
                .isEqualTo(ReservationIntentState.PENDING);

        reservationAdapter.release(request.orderId(), request.ticketTypeId(), request.userId(), request.quantity());
        assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(2);
        assertThat(redisTemplate.opsForValue().get(userLimitKey(request))).isEqualTo("0");
    }

    @Test
    void warmUpIsIdempotentBeforeSaleButNeverOverwritesLiveStock() {
        reservationAdapter.warmUp(TICKET_TYPE_ID, 5);
        reservationAdapter.warmUp(TICKET_TYPE_ID, 5);
        assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(5);

        ReservationRequest request = request(1, 5, Duration.ofMinutes(1), UUID.randomUUID());
        assertThat(reservationAdapter.tryReserve(request)).isEqualTo(ReservationResult.SUCCESS);
        assertThatThrownBy(() -> reservationAdapter.warmUp(TICKET_TYPE_ID, 6))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to overwrite live stock");
        assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(4);
    }

    @Test
    void concurrentCallersNeverOversellWarmedStock() throws Exception {
        reservationAdapter.warmUp(TICKET_TYPE_ID, 20);
        int attempts = 60;
        ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch start = new CountDownLatch(1);
        List<Future<ReservationResult>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < attempts; index++) {
                UUID userId = UUID.randomUUID();
                futures.add(executor.submit(() -> {
                    start.await();
                    return reservationAdapter.tryReserve(
                            request(1, 100, Duration.ofMinutes(1), userId));
                }));
            }
            start.countDown();

            long successes = count(futures, ReservationResult.SUCCESS);
            assertThat(successes).isEqualTo(20);
            assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isZero();
            assertThat(redisTemplate.opsForValue().get(RedisReservationKeys.stock(TICKET_TYPE_ID)))
                    .isEqualTo("0");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCallersCannotExceedPerUserLimit() throws Exception {
        reservationAdapter.warmUp(TICKET_TYPE_ID, 50);
        UUID userId = UUID.randomUUID();
        int attempts = 30;
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReservationResult>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < attempts; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return reservationAdapter.tryReserve(
                            request(1, 3, Duration.ofMinutes(1), userId));
                }));
            }
            start.countDown();

            assertThat(count(futures, ReservationResult.SUCCESS)).isEqualTo(3);
            assertThat(redisTemplate.opsForValue().get(userLimitKey(
                    request(1, 3, Duration.ofMinutes(1), userId)))).isEqualTo("3");
            assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(47);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void claimsAndIntentStateTransitionsAreRecoverableAndIdempotent() {
        reservationAdapter.warmUp(TICKET_TYPE_ID, 3);
        ReservationRequest request = request(1, 3, Duration.ofMinutes(1), UUID.randomUUID());
        assertThat(reservationAdapter.tryReserve(request)).isEqualTo(ReservationResult.SUCCESS);

        assertThat(reservationAdapter.tryClaim(request.orderId(), "relay-a", Duration.ofSeconds(1)))
                .isTrue();
        assertThat(reservationAdapter.tryClaim(request.orderId(), "relay-b", Duration.ofSeconds(1)))
                .isFalse();
        reservationAdapter.releaseClaim(request.orderId(), "relay-b");
        assertThat(reservationAdapter.tryClaim(request.orderId(), "relay-b", Duration.ofSeconds(1)))
                .isFalse();
        reservationAdapter.releaseClaim(request.orderId(), "relay-a");
        assertThat(reservationAdapter.tryClaim(request.orderId(), "relay-b", Duration.ofSeconds(1)))
                .isTrue();

        Instant retryAt = Instant.now().plusSeconds(30);
        reservationAdapter.reschedule(request.orderId(), 2, retryAt);
        ReservationIntent rescheduled = reservationAdapter.findByOrderId(request.orderId()).orElseThrow();
        assertThat(rescheduled.state()).isEqualTo(ReservationIntentState.PENDING);
        assertThat(rescheduled.retryCount()).isEqualTo(2);
        assertThat(rescheduled.nextRetryAt()).isEqualTo(retryAt);
        assertThat(reservationAdapter.findDue(Instant.now(), 10)).isEmpty();

        reservationAdapter.markEnqueued(request.orderId());
        assertThat(reservationAdapter.findByOrderId(request.orderId()).orElseThrow().state())
                .isEqualTo(ReservationIntentState.ENQUEUED);
        assertThat(reservationAdapter.findDue(Instant.now().plusSeconds(60), 10)).isEmpty();

        reservationAdapter.markOrderCreated(request.orderId());
        assertThat(reservationAdapter.findByOrderId(request.orderId()).orElseThrow().state())
                .isEqualTo(ReservationIntentState.ORDER_CREATED);

        ReservationRequest expiredRequest = request(1, 3, Duration.ofMinutes(1), UUID.randomUUID());
        assertThat(reservationAdapter.tryReserve(expiredRequest)).isEqualTo(ReservationResult.SUCCESS);
        reservationAdapter.markExpired(expiredRequest.orderId());
        assertThat(reservationAdapter.findByOrderId(expiredRequest.orderId()).orElseThrow().state())
                .isEqualTo(ReservationIntentState.EXPIRED);
        assertThat(reservationAdapter.findDue(Instant.now().plusSeconds(60), 10)).isEmpty();
    }

    @Test
    void reconciliationIndexesStaleEnqueuedAndExpiredIntentsButNotCompletedOnes() {
        reservationAdapter.warmUp(TICKET_TYPE_ID, 3);
        ReservationRequest request = request(1, 3, Duration.ofSeconds(1), UUID.randomUUID());
        assertThat(reservationAdapter.tryReserve(request)).isEqualTo(ReservationResult.SUCCESS);

        reservationAdapter.markEnqueued(request.orderId());
        ReservationIntent enqueued = reservationAdapter.findByOrderId(request.orderId()).orElseThrow();
        assertThat(enqueued.enqueuedAt()).isNotNull();
        assertThat(reservationAdapter.findStaleEnqueued(
                Instant.now().plusSeconds(5), Duration.ofSeconds(1), 10))
                .extracting(ReservationIntent::orderId)
                .containsExactly(request.orderId());

        reservationAdapter.markExpired(request.orderId());
        assertThat(reservationAdapter.findExpired(Instant.now().plusSeconds(5), 10))
                .extracting(ReservationIntent::orderId)
                .containsExactly(request.orderId());

        ReservationRequest completedRequest = request(1, 3, Duration.ofSeconds(1), UUID.randomUUID());
        assertThat(reservationAdapter.tryReserve(completedRequest)).isEqualTo(ReservationResult.SUCCESS);
        reservationAdapter.markOrderCreated(completedRequest.orderId());
        reservationAdapter.markCompleted(completedRequest.orderId());
        assertThat(reservationAdapter.findByOrderId(completedRequest.orderId()).orElseThrow().state())
                .isEqualTo(ReservationIntentState.COMPLETED);
        assertThat(redisTemplate.hasKey(holdKey(completedRequest))).isFalse();
        assertThat(redisTemplate.opsForValue().get(userLimitKey(completedRequest))).isEqualTo("1");
        assertThat(reservationAdapter.getAvailableStock(TICKET_TYPE_ID)).isEqualTo(1);
        reservationAdapter.reschedule(completedRequest.orderId(), 5, Instant.now().plusSeconds(30));
        assertThat(reservationAdapter.findByOrderId(completedRequest.orderId()).orElseThrow().state())
                .isEqualTo(ReservationIntentState.COMPLETED);
        assertThat(reservationAdapter.findDue(Instant.now().plusSeconds(60), 10))
                .extracting(ReservationIntent::orderId)
                .doesNotContain(completedRequest.orderId());
        assertThat(reservationAdapter.findExpired(Instant.now().plusSeconds(5), 10))
                .extracting(ReservationIntent::orderId)
                .doesNotContain(completedRequest.orderId());
    }

    private ReservationRequest request(int quantity, int maxPerUser, Duration holdDuration, UUID userId) {
        Instant reservedAt = Instant.now();
        return new ReservationRequest(
                Order.generateId(),
                userId,
                EVENT_ID,
                TICKET_TYPE_ID,
                quantity,
                Money.of(new BigDecimal("125000.00")),
                maxPerUser,
                Math.toIntExact(holdDuration.toSeconds()),
                reservedAt,
                reservedAt.plus(holdDuration));
    }

    private String holdKey(ReservationRequest request) {
        return RedisReservationKeys.hold(request.orderId());
    }

    private String userLimitKey(ReservationRequest request) {
        return RedisReservationKeys.userLimit(request.ticketTypeId(), request.userId());
    }

    private long count(List<Future<ReservationResult>> futures, ReservationResult expected)
            throws InterruptedException, ExecutionException {
        long count = 0;
        for (Future<ReservationResult> future : futures) {
            if (future.get() == expected) {
                count++;
            }
        }
        return count;
    }
}
