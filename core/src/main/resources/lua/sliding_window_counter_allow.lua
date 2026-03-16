-- Sliding Window Counter Rate Limiter - Allow Request (Optimized)
--
-- KEYS[1] = previous count key
-- KEYS[2] = current count key
-- KEYS[3] = window start key
--
-- ARGV[1] = maxRequests (limit per window)
-- ARGV[2] = windowSizeMillis (window size in milliseconds)
-- ARGV[3] = currentTime (current timestamp in milliseconds)
-- ARGV[4] = ttl (seconds)
--
-- Returns a table with:
-- [1] = allowed (1 or 0)
-- [2] = previousCount
-- [3] = currentCount
-- [4] = estimatedCount
-- [5] = windowStart

local previousKey = KEYS[1]
local currentKey = KEYS[2]
local windowStartKey = KEYS[3]

local maxRequests = tonumber(ARGV[1])
local windowSizeMillis = tonumber(ARGV[2])
local currentTime = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

-- Calculate which window we should be in
local expectedWindowStart = math.floor(currentTime / windowSizeMillis) * windowSizeMillis

-- Get stored values
local previousCount = tonumber(redis.call('GET', previousKey)) or 0
local currentCount = tonumber(redis.call('GET', currentKey)) or 0
local storedWindowStart = tonumber(redis.call('GET', windowStartKey))

-- Check if we've moved to a new window
if not storedWindowStart or expectedWindowStart > storedWindowStart then
    -- Rotate windows: current → previous
    previousCount = currentCount
    currentCount = 0
    storedWindowStart = expectedWindowStart

    -- Store rotated values
    redis.call('SET', previousKey, previousCount, 'EX', ttl)
    redis.call('SET', currentKey, currentCount, 'EX', ttl)
    redis.call('SET', windowStartKey, storedWindowStart, 'EX', ttl)
end

-- Calculate sliding window estimate
local timeIntoCurrentWindow = currentTime - storedWindowStart
local windowProgress = timeIntoCurrentWindow / windowSizeMillis
local previousWindowWeight = math.max(0, 1.0 - windowProgress)

local estimatedCount = (previousCount * previousWindowWeight) + currentCount

-- Check if request is allowed
local allowed = 0
if estimatedCount < maxRequests then
    -- Increment current counter
    currentCount = currentCount + 1
    redis.call('SET', currentKey, currentCount, 'EX', ttl)
    allowed = 1
end

-- Return all data in one response (no additional calls needed!)
return {allowed, previousCount, currentCount, estimatedCount, storedWindowStart}