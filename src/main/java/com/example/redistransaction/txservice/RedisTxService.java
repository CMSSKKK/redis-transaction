package com.example.redistransaction.txservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RedisTxService implements RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    public void incr(String key, boolean isException) {
        stringRedisTemplate.opsForValue().increment(key);
        if(isException) {
            throw new RuntimeException();
        }

    }

    @Override
    public RedisDto incrAndCopy(String originkey, String newkey, int count) {
        stringRedisTemplate.opsForValue().increment(originkey, count);
        String value = stringRedisTemplate.opsForValue().get(originkey);
        log.info("after increment, get(originKey) = {}", value);
        stringRedisTemplate.opsForValue().set(newkey, value);

        return new RedisDto(newkey, value);
    }

}
