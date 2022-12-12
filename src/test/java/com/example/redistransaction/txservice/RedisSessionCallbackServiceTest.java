package com.example.redistransaction.txservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RedisSessionCallbackServiceTest {

    @Autowired
    private RedisSessionCallbackService sessionCallbackService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final String key = "txKey";
    private final String copyKey = "copyKey";

    @BeforeEach
    void setUp() {
        redisTemplate.opsForValue().set(key, "1");
    }

    @Test
    @DisplayName("예외가 발생하지 않으면, 정상적으로 key의 값이 1 증가한다.")
    void incr_session_test() {
        // when
        sessionCallbackService.incr(key, false);

        // then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(2);

    }

    @Test
    @DisplayName("트랜잭션안에서 exception이 발생하면 transaction이 discard된다.")
    void incr_session_test_throw_exception() {
        // when
        assertThatThrownBy(() -> sessionCallbackService.incr(key, true))
                .isInstanceOf(RuntimeException.class);

        //then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(1);

    }

    @Test
    @DisplayName("트랜잭션 내부에서 값을 조회해서 활용하고자하면 Exception이 발생한다.")
    void incrAndCopy_sessionTest_exception() {

        assertThatThrownBy(() -> sessionCallbackService.incrAndCopy(key, copyKey, 10))
                .isInstanceOf(IllegalArgumentException.class);

        //then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(1);
    }


}
