package com.example.redistransaction.txservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSessionCallbackService implements RedisService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void incr(String key, boolean isException) {
        List<Object> execute = redisTemplate.execute(new SessionCallback<>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) throws DataAccessException {
                operations.multi();
                operations.opsForValue().increment((K) key);

                if (isException) {
                    throw new RuntimeException();
                }
                return operations.exec();
            }
        });
        assert execute != null;
        log.info("결과는={}", execute.get(0));
    }

    @Override
    public RedisDto incrAndCopy(String originkey, String newkey, int count) {
        List<Object> result = redisTemplate.execute(new SessionCallback<>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) throws DataAccessException {
                operations.multi();
                operations.opsForValue().increment((K) originkey, count);
                String value = (String) operations.opsForValue().get(originkey);
                log.info("after increment, get(originKey) = {}", value);
                operations.opsForValue().set((K) newkey, (V) value);

                return operations.exec();
            }
        });
        return new RedisDto(newkey, (String) result.get(1));
    }




}
