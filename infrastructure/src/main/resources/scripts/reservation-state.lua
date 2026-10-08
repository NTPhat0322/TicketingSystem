-- KEYS[1] recoverable reservation-intent hash
-- KEYS[2] pending-intent sorted set
-- KEYS[3] short-lived hold hash (required only for action 5)
-- ARGV[1] action: 1=reschedule, 2=enqueued, 3=order-created, 4=expired, 5=completed
-- ARGV[2] retry count
-- ARGV[3] next retry time in ISO-8601 form for the Hash
-- ARGV[4] next retry time in epoch milliseconds for the Sorted Set score

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

local action = tonumber(ARGV[1])
local currentState = redis.call('HGET', KEYS[1], 'state')
local orderId = redis.call('HGET', KEYS[1], 'orderId')

if action == 1 then
    if currentState == 'RELEASED' or currentState == 'EXPIRED' or currentState == 'COMPLETED' then
        return 0
    end
    redis.call('HSET', KEYS[1],
        'state', 'PENDING',
        'retryCount', ARGV[2],
        'nextRetryAt', ARGV[3])
    redis.call('ZADD', KEYS[2], ARGV[4], orderId)
    return 1
end

if action == 2 then
    if currentState == 'RELEASED' or currentState == 'EXPIRED' or currentState == 'COMPLETED' then
        return 0
    end
    redis.call('HSET', KEYS[1], 'state', 'ENQUEUED', 'enqueuedAt', ARGV[3])
    redis.call('ZREM', KEYS[2], orderId)
    return 1
end

if action == 3 then
    if currentState == 'RELEASED' or currentState == 'EXPIRED' or currentState == 'COMPLETED' then
        return 0
    end
    redis.call('HSET', KEYS[1], 'state', 'ORDER_CREATED')
    redis.call('ZREM', KEYS[2], orderId)
    return 1
end

if action == 4 then
    if currentState == 'RELEASED' or currentState == 'COMPLETED' then
        return 0
    end
    redis.call('HSET', KEYS[1], 'state', 'EXPIRED')
    redis.call('ZREM', KEYS[2], orderId)
    return 1
end

if action == 5 then
    if currentState == 'RELEASED' or currentState == 'EXPIRED' or currentState == 'COMPLETED' then
        return 0
    end
    redis.call('HSET', KEYS[1], 'state', 'COMPLETED')
    redis.call('ZREM', KEYS[2], orderId)
    -- Payment has won. Remove only the live hold metadata: the user-limit counter is deliberately
    -- retained because a paid ticket counts toward the sale-wide max-per-user rule.
    redis.call('DEL', KEYS[3])
    return 1
end

return 0
