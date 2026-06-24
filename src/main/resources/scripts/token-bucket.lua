-- Token Bucket Rate Limiter (Redis Lua script)
-- KEYS[1]   -> bucket key (e.g. user ID, IP address, API key)
-- ARGV[1]   -> capacity (maximum tokens the bucket can hold)
-- ARGV[2]   -> refill_rate (tokens added per second)
-- ARGV[3]   -> current_time_ms (current epoch time in milliseconds)
--
-- Returns 1 if a token was consumed (request allowed),
--         0 if no tokens available (request denied)

local key           = KEYS[1]
local capacity      = tonumber(ARGV[1])
local refillRate    = tonumber(ARGV[2])
local nowMs         = tonumber(ARGV[3])

-- Get current bucket state
local tokens        = redis.call("HGET", key, "tokens")
local lastRefill    = redis.call("HGET", key, "last_refill_time")

-- Initialize bucket if it doesn't exist
if tokens == false then
    tokens       = capacity
    lastRefill   = nowMs
else
    tokens       = tonumber(tokens)
    lastRefill   = tonumber(lastRefill)
end

-- Calculate time elapsed since last refill (in seconds)
local elapsedSeconds = (nowMs - lastRefill) / 1000.0

-- Refill tokens based on elapsed time
local refillTokens = elapsedSeconds * refillRate
tokens = tokens + refillTokens

-- Cap tokens at capacity
if tokens > capacity then
    tokens = capacity
end

-- Update last refill time to now
lastRefill = nowMs

-- Try to consume one token
if tokens >= 1 then
    tokens = tokens - 1
    redis.call("HSET", key, "tokens", tokens, "last_refill_time", lastRefill)
    -- Set TTL to the estimated time to fully drain the bucket + 1 second buffer
    local ttl = math.ceil(capacity / refillRate) + 1
    redis.call("EXPIRE", key, ttl)
    return 1
else
    redis.call("HSET", key, "last_refill_time", lastRefill)
    return 0
end