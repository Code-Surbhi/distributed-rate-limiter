-- Token Bucket Rate Limiter - Allow Request
--
-- KEYS[1] = tokens key (e.g., "ratelimit:tokenbucket:user123:tokens")
-- KEYS[2] = lastRefill key (e.g., "ratelimit:tokenbucket:user123:lastRefill")
--
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refillRate (tokens per second)
-- ARGV[3] = currentTime (milliseconds)
-- ARGV[4] = ttl (seconds)
--
-- Returns:
-- 1 if request allowed
-- 0 if rate limited

local tokensKey = KEYS[1]
local lastRefillKey = KEYS[2]

local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local currentTime = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

-- Get current state
local currentTokens = redis.call('GET', tokensKey)
local lastRefillTime = redis.call('GET', lastRefillKey)

-- First request - initialize
if not currentTokens or not lastRefillTime then
    redis.call('SET', tokensKey, capacity - 1, 'EX', ttl)
    redis.call('SET', lastRefillKey, currentTime, 'EX', ttl)
    return 1  -- Allow first request
end

-- Convert to numbers
currentTokens = tonumber(currentTokens)
lastRefillTime = tonumber(lastRefillTime)

-- Calculate refill
local timePassed = (currentTime - lastRefillTime) / 1000  -- Convert to seconds
local tokensToAdd = math.floor(timePassed * refillRate)

-- Calculate new token count
local newTokens = math.min(currentTokens + tokensToAdd, capacity)

-- Check if we have tokens
if newTokens > 0 then
    -- Consume one token
    redis.call('SET', tokensKey, newTokens - 1, 'EX', ttl)

    -- Update refill time if we added tokens
    if tokensToAdd > 0 then
        redis.call('SET', lastRefillKey, currentTime, 'EX', ttl)
    end

    return 1  -- Request allowed
end

return 0  -- Rate limited