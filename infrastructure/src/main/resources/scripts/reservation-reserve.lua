-- KEYS[1] stock key
-- KEYS[2] user-limit counter
-- KEYS[3] short-lived hold hash
-- KEYS[4] recoverable reservation-intent hash
-- KEYS[5] pending-intent sorted set
-- ARGV: orderId, userId, eventId, ticketTypeId, quantity, unitPrice,
--       maxPerUser, holdDurationSec, reservedAt, expiresAt, nextRetryAt,
--       holdTtlMillis, nextRetryAtMillis

local stockValue = redis.call('GET', KEYS[1])
if not stockValue then
    return 3
end

local stock = tonumber(stockValue)
local quantity = tonumber(ARGV[5])
local maxPerUser = tonumber(ARGV[7])
local holdTtlMillis = tonumber(ARGV[12])
if not stock or not quantity or not maxPerUser or not holdTtlMillis then
    return 3
end

-- A generated order id must not reserve twice if a caller accidentally replays the same command.
if redis.call('EXISTS', KEYS[3]) == 1 or redis.call('EXISTS', KEYS[4]) == 1 then
    return 4
end

if stock < quantity then
    return 1
end

local currentUserQuantity = tonumber(redis.call('GET', KEYS[2]) or '0') or 0
if currentUserQuantity + quantity > maxPerUser then
    return 2
end

redis.call('DECRBY', KEYS[1], quantity)
redis.call('INCRBY', KEYS[2], quantity)

redis.call('HSET', KEYS[3],
    'orderId', ARGV[1],
    'userId', ARGV[2],
    'eventId', ARGV[3],
    'ticketTypeId', ARGV[4],
    'quantity', ARGV[5],
    'unitPrice', ARGV[6],
    'maxPerUser', ARGV[7],
    'holdDurationSec', ARGV[8],
    'reservedAt', ARGV[9],
    'expiresAt', ARGV[10],
    'state', 'HELD',
    'holdState', 'HELD')
redis.call('PEXPIRE', KEYS[3], holdTtlMillis)

redis.call('HSET', KEYS[4],
    'orderId', ARGV[1],
    'userId', ARGV[2],
    'eventId', ARGV[3],
    'ticketTypeId', ARGV[4],
    'quantity', ARGV[5],
    'unitPrice', ARGV[6],
    'maxPerUser', ARGV[7],
    'holdDurationSec', ARGV[8],
    'reservedAt', ARGV[9],
    'expiresAt', ARGV[10],
    'state', 'PENDING',
    'holdState', 'HELD',
    'retryCount', '0',
    'nextRetryAt', ARGV[11])
redis.call('ZADD', KEYS[5], ARGV[13], ARGV[1])

return 0
