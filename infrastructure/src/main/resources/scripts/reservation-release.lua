-- KEYS[1] short-lived hold hash
-- KEYS[2] recoverable reservation-intent hash
-- KEYS[3] stock key
-- KEYS[4] user-limit counter
-- KEYS[5] pending-intent sorted set
-- ARGV: orderId, ticketTypeId, userId, quantity

if redis.call('EXISTS', KEYS[2]) == 0 then
    return 3
end

local holdState = redis.call('HGET', KEYS[2], 'holdState') or 'HELD'
if holdState == 'RELEASED' then
    return 2
end

local storedOrderId = redis.call('HGET', KEYS[2], 'orderId')
local storedTicketTypeId = redis.call('HGET', KEYS[2], 'ticketTypeId')
local storedUserId = redis.call('HGET', KEYS[2], 'userId')
local storedQuantity = tonumber(redis.call('HGET', KEYS[2], 'quantity') or '0') or 0
local quantity = tonumber(ARGV[4])
if storedOrderId ~= ARGV[1]
        or storedTicketTypeId ~= ARGV[2]
        or storedUserId ~= ARGV[3]
        or not quantity
        or storedQuantity ~= quantity then
    return 4
end

if redis.call('EXISTS', KEYS[3]) == 0 then
    return 6
end

local currentUserQuantity = tonumber(redis.call('GET', KEYS[4]) or '0') or 0
if currentUserQuantity < quantity then
    return 5
end

redis.call('INCRBY', KEYS[3], quantity)
redis.call('DECRBY', KEYS[4], quantity)
redis.call('HSET', KEYS[2], 'holdState', 'RELEASED')

-- An expiry decision may already have been recorded. Preserve EXPIRED while still recording that
-- the counters were restored; a normal cancellation/release becomes RELEASED.
local currentState = redis.call('HGET', KEYS[2], 'state')
if currentState ~= 'EXPIRED' then
    redis.call('HSET', KEYS[2], 'state', 'RELEASED')
end
redis.call('ZREM', KEYS[5], ARGV[1])
redis.call('DEL', KEYS[1])

return 1
