package com.example.redistransaction.txservice;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisLuaService implements RedisService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> incrAndCopyScript;
    private final RedisScript<Void> incrScript;

    @Override
    @Transactional
    public void incr(String key, boolean isException) {
        redisTemplate.execute(incrScript, List.of(key));
        if (isException) {
            throw new RuntimeException();
        }
    }

    @Override
    public RedisDto incrAndCopy(String originkey, String newkey, int count) {
        Long value = redisTemplate.execute(incrAndCopyScript, List.of(originkey, newkey), String.valueOf(count));

        return new RedisDto(newkey, String.valueOf(value));
    }
}
