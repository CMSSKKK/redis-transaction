package com.example.redistransaction.txservice;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.*;


@SpringBootTest
class RedisServiceTest {

    @Autowired
    private RedisTxService redisTxService;

    @Autowired
    private RedisSessionCallbackService sessionCallbackService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final String key = "txKey";
    private final String copyKey = "copyKey";

    @BeforeEach
    void setUp() {
        redisTemplate.opsForValue().set(key, "1");
        redisTemplate.delete(copyKey);
    }

    @Test
    void incr_tx_test() {
        // when
        redisTxService.incr(key, false);

        // then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(2);
    }

    @Test
    @DisplayName("트랜잭션안에서 exception이 발생하면 transaction이 discard 되는지 확인해본다.")
    void incr_tx_test_throw_exception() {
        // when
        assertThatThrownBy(() -> redisTxService.incr(key, true))
                .isInstanceOf(RuntimeException.class);
        // then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(1);
    }

    @Test
    void incr_session_test() {
        // when
        sessionCallbackService.incr(key, false);

        // then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(2);

    }

    @Test
    @DisplayName("트랜잭션안에서 exception이 발생하면 transaction이 discard 되는지 확인해본다.")
    void incr_session_test_throw_exception() {
        // when
        assertThatThrownBy(() -> sessionCallbackService.incr(key, true))
                .isInstanceOf(RuntimeException.class);

        //then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(1);

    }

    @Test
    void incrAndCopy_txTest_exception() {

        assertThatThrownBy(() -> redisTxService.incrAndCopy(key, copyKey, 10))
                .isInstanceOf(IllegalArgumentException.class);

        //then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(1);
    }

    @Test
    void incrAndCopy_sessionTest_exception() {

        assertThatThrownBy(() -> sessionCallbackService.incrAndCopy(key, copyKey, 10))
                .isInstanceOf(IllegalArgumentException.class);

        //then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(1);
    }
}
