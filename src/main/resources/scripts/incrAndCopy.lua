redis.call('INCRBY', KEYS[1], ARGV[1])
local value = redis.call('GET', KEYS[1])
redis.call('SET', KEYS[1], value)
return tonumber(value)
