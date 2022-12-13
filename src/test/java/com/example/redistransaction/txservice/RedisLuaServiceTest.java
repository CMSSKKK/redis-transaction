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
class RedisLuaServiceTest {

    @Autowired
    private RedisLuaService luaService;

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
    @DisplayName("값을 증가시키고 새로운 키에 값을 저장하고 새로운 키와 값을 반환한다.")
    void incrAndCopy_luaTest() {
        //when
        RedisDto redisDto = luaService.incrAndCopy(key, copyKey, 10);
        //then
        assertThat(redisDto.getKey()).isEqualTo(copyKey);
        assertThat(Integer.parseInt(redisDto.getValue())).isEqualTo(11);
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(11);
    }


    @Test
    @DisplayName("예외가 발생해도, rollback이 되지 않는다.")
    void incr_lua_in_tx_test_throw_exception() {

        // when
        assertThatThrownBy(() -> luaService.incr(key, true))
                .isInstanceOf(RuntimeException.class);
        // then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(1);
    }

}
