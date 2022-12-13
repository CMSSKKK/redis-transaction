# Redis Transaction, Lua script, Pipeline (SpringDataRedis의 사용법)



- Spring에서 Redis를 연동하고 활용하는 연습을 진행 중 입니다.
- Spirng boot에서 Redis를 연동하여 사용하는 방법과 원리에 대해서 이해해보고자 하는 글입니다.
- Redis는 local 환경의 Docker 컨테이너를 활용했습니다.



## 1. Redis Transaction

- 트랜잭션이란 여러가지 명령어들을 처리하는 하나의 단위입니다.

  ```
  127.0.0.1:6379> multi
  OK
  127.0.0.1:6379(TX)> incr click
  QUEUED
  127.0.0.1:6379(TX)> incr click
  QUEUED
  127.0.0.1:6379(TX)> exec
  1) (integer) 1
  2) (integer) 2
  ```

- Redis-cli 에서 명령을 보면 `multi`로 트랜잭션을 시작하고, 여러 명령어의 실행 결과값을 `exec` 하면 명령 순서에 따라 결과값을 반환하는 것을 볼 수 있습니다.

```
127.0.0.1:6379> multi
OK
127.0.0.1:6379(TX)> incr click
QUEUED
127.0.0.1:6379(TX)> discard
OK
127.0.0.1:6379> get click
"2"
```

- 그리고 트랜잭션 내에서 dicard를 입력하면 이전에 입력했던 명령들이 저장되지 않는 것을 볼 수 있습니다.

```
#### 커넥션 1 ###
127.0.0.1:6379> get click
"6"
127.0.0.1:6379> watch click
OK
127.0.0.1:6379> multi
OK
127.0.0.1:6379(TX)> incr click
QUEUED
127.0.0.1:6379(TX)> set secondkey secret
QUEUED
###############

#### 커넥션 2 ###
127.0.0.1:6379> get click  
"6"
127.0.0.1:6379> incr click
(integer) 7
127.0.0.1:6379>
###############

#### 커넥션 1 ###
127.0.0.1:6379(TX)> exec
(nil) <- 명령 실패
127.0.0.1:6379> get secondkey
(nil)
127.0.0.1:6379> get click
"7"
###############

```

- 또한 watch 명령어를 통해 하나의 key에 optimistic lock을 걸고, Transaction 도중에 다른 커넥션에서 해당 key에 변경을 주게되면 트랜잭션 내의 명령어가 모두 실패합니다.
- 위의 예시는 2개의 커넥션에서의 예시로 명령어 입력 순서대로 작성했습니다.
- 결과적으로 트랜잭션이 실패해서 click을 1 증가시키지 못했고, secondkey가 저장되지않습니다.

- 관계형 DB를 사용하면서 트랜잭션에 대해서 공부하신 분들이라면 위의 명령어들과 작동원리에 대해서 가볍게 이해하셨을 겁니다.
- `exec` 명령어는 commit, `discard`는 rollback이라고 이해하실 수 있습니다.

하지만 이를 동일한 개념으로 이해하고 실제로 적용하려고 한다면 문제가 발생할 수 있습니다.

기억해두어야 할 점은 아래와 같이 5가지입니다.

### 1. Redis의 트랜잭션에서는 트랜잭션 동안의 명령어 결과를 확인할 수 없습니다.  (Spring에서 활용시 가장 중요한 점)

### 2. 트랜잭션 내부에서 완전히 잘못된(사용할 수 없는) 명령어(syntax error)를 사용하면 트랜잭션은 Discard됩니다.

### 3. 트랜잭션 내부에서 레디스 자료구조를 잘못 사용한 명령어는 트랜잭션에 영향을 주지 않습니다. (다른 명령들은 정상적으로 실행, 반환됩니다.)

### 4. watch를 통한 lock이 없다면 명령어의 모음일 뿐 트랜잭션간의 격리 수준이 없습니다. (독립성은 보장) exec 시점에 모든 명령어가 순차적으로 실행되는 것과 같습니다.

### 5. Redis에서는 성능을 위해서 rollback의 개념이 없습니다.





## 2. Spring에서 Transaction 사용하기

- 설정에 관해서는 [@ConfigurationProperties](https://velog.io/@cmsskkk/SpringBoot-ConfigurationProperties) 를 설명하는 글에서 Redis 설정에 관해서 예시를 들었으며, 다양한 참고자료가 많으니 참고하시면 될것 같습니다.
- Dependency : `implementation 'org.springframework.boot:spring-boot-starter-data-redis'`
- Spring에서 Transaction을 사용하는 방법은 `SessionCallback`을 사용하는 방법과 `@Transactional` 어노테이션을 사용하는 방법이있습니다.

```
public interface RedisService {

    void incr(String key, boolean isException);

    RedisDto incrAndCopy(String originkey, String newkey, int count);
}
```

- 여러 방식의 활용을 테스트하기 위해서 RedisService라는 인터페이스를 구현하고 시작합니다.
- `incr()`메서드의 경우 uncheckException이 발생했을 때 데이터가 저장이 되는지를 확인하고자 합니다.
- `incrAndCopy()` 메서드의 경우 트랜잭션의 활용할 때의 주의점에 대해서 설명하고자합니다.

### @Transactional

- Spring Data Redis에서는 PlatformTransactionManager가 구현되어 있지 않습니다.
- 그래서 기본적으로 Redis만을 사용하려고한다면 @Transactional 어노테이션을 통한 트랜잭션의 지원을 받을 수 없습니다.
- 그래서 DataSource와 TransactionManager를 Bean으로 등록하고 트랜잭션을 사용할 수 있도록 설정해야합니다.
- 대부분 Redis만을 활용하는 경우가 없기때문에 저는 간단하게 적용을 위해서 Spring data Jpa와 embedded h2를 활용해서 JpaTransactionManager를 통해서 트랜잭션을 지원했습니다.
- 그리고 redisTemplate의 경우 트랜잭션을 지원할 수 있도록 설정하고, 기본적인 트랜잭션 테스트만을 위해서 StringRedisTemplate만을 Bean으로 등록했습니다.

![스크린샷 2022-12-13 오전 1.29.09](/Users/cmsskkk/Desktop/스크린샷 2022-12-13 오전 1.29.09.png)

- @Transactional 어노테이션을 적용하는 구현입니다.

```java
@Service
@RequiredArgsConstructor
@Transactional // 클래스 단위로 적용 
@Slf4j
public class RedisTxService implements RedisService {

    private final StringRedisTemplate stringRedisTemplate;
  
		@Override 
    public void incr(String key, boolean isException) {
				// 하나의 Strings 자료구조의 key의 value를 1 증가 시키는 메서드입니다. 
        stringRedisTemplate.opsForValue().increment(key);
      	// 예외 상황을 위해서 isException이 true이면 RuntimeException을 던집니다.
        if(isException) {
            throw new RuntimeException();
        }

    }

    @Override // 무조건 실패하는 로직입니다.
    public RedisDto incrAndCopy(String originkey, String newkey, int count) {
      	// 기존키의 값을 count만큼 증가시킵니다.
        stringRedisTemplate.opsForValue().increment(originkey, count); 
      	// 증가된 값을 가져옵니다.
        String value = stringRedisTemplate.opsForValue().get(originkey);
        log.info("after increment, get(originKey) = {}", value);
      
      	// 새로운 key에 증가된 값을 저장합니다.
        stringRedisTemplate.opsForValue().set(newkey, value);
				
      	// 새로 저장된 key와 value를 Dto로 만들어 반환합니다. 
        return new RedisDto(newkey, value);
    }

}
```

- 먼저 incr() 트랜잭션에서 예외 발생시에 대한 테스트입니다.

```java
@SpringBootTest
class RedisTxServiceTest {

    @Autowired
    private RedisTxService redisTxService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final String key = "txKey";
    private final String copyKey = "copyKey";

    @BeforeEach
    void setUp() { 
        redisTemplate.opsForValue().set(key, "1"); // test마다 시도 전에 txKey의 값을 1로 설정합니다. 
    }

    @Test
    @DisplayName("예외가 발생하지 않으면, 정상적으로 key의 값이 1 증가한다.")
    void incr_tx_test() {
        // when
        redisTxService.incr(key, false); // 예외 발생 X

        // then
        String value = redisTemplate.opsForValue().get(key); // 결과 조회
        assertThat(Integer.parseInt(value)).isEqualTo(2); // 1 증가 성공
    }

    @Test
    @DisplayName("트랜잭션안에서 exception이 발생하면 transaction이 discard된다.")
    void incr_tx_test_throw_exception() {
        // when
        assertThatThrownBy(() -> redisTxService.incr(key, true)) // 예외 발생
                .isInstanceOf(RuntimeException.class); // RuntimeException 발생
        // then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(1); // 기존 그대로의 값
    }

}
```

- redis에서의 트랜잭션 또한 uncheckedException이 발생했을 때, 데이터를 저장하지 않는 것을 확인할 수 있습니다.

- 다음으로 incrAndCopy() 테스트입니다.
- 위에서 Spring에서 redis를 트랜잭션으로 활용할 때의 가장 중요한 점을 테스트합니다. Transaction 내부에서는 값을 조회해서 조작할 수 없는 것을 확인할 수 있습니다.

```java
@Test
@DisplayName("트랜잭션 내부에서 값을 조회해서 활용하고자하면 Exception이 발생한다.")
void incrAndCopy_txTest_exception() {
	
  assertThatThrownBy(() -> redisTxService.incrAndCopy(key, copyKey, 10)) 
    .isInstanceOf(IllegalArgumentException.class); // null값을 조작하려고해서 IllegalArgumentException 발생 

  //then
  String value = redisTemplate.opsForValue().get(key);
  assertThat(Integer.parseInt(value)).isEqualTo(1); // 값 변동없음 
}
```

- 위에서 redis-cli 예시와 같이 트랜잭션 동안의 명령어의 결과값은 queue에 저장되고, exec 될때 모두 반환해줍니다.

  ![스크린샷 2022-12-13 오후 4.37.16](/Users/cmsskkk/Desktop/스크린샷 2022-12-13 오후 4.37.16.png)

- ValueOperation의 get 메서드를 확인해보면 transaction과 pipeline 내부에서 사용시 null을 반환함을 정의합니다.

- 그렇기 때문에 해당 메서드의 로직처럼 트랜잭션 내부에서 **데이터를 조회해서 그 데이터를 토대로 조건을 걸거나, 조작할 수 없습니다. **

- 이 부분이 기존 관계형 DB의 트랜잭션과 레디스의 트랜잭션의 가장 큰 차이점입니다.

```java
log.info("after increment, get(originKey) = {}", value); // null
```

### 

### SessionCallback과 RedisTemplate

- 설정은 동일합니다. 이 때는 트랜잭션매니저의 도움을 받지않아도 트랜잭션을 적용할 수 있습니다.
- redisTemplate의 excute 메서드 안에 SessionCallback 만들어서 적용하는 방법입니다.
- SessionCallbackdml excute 내부에서 multi()로 트랜잭션을 시작하지않으면 예외가 발생합니다.
- 그리고 multi() 메서드를 통해서 transaction을 시작하고 명령어를 입력합니다.
- 그리고 트랜잭션을 마무리할 때, exec() 메서드를 통해서 트랜잭션을 완료합니다.
- 그리고 반환값은 List의 형태로 명령어의 결과값을 순차적으로 담아서 반환합니다.

```java
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
                operations.multi(); // 트랜잭션 시작 
                operations.opsForValue().increment((K) key); // 1 증가

                if (isException) {
                    throw new RuntimeException();
                }
                return operations.exec();
            }
        });
        log.info("결과는={}", execute.get(0));
    }

    @Override
    public RedisDto incrAndCopy(String originkey, String newkey, int count) {
        List<Object> result = redisTemplate.execute(new SessionCallback<>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) throws DataAccessException {
                operations.multi(); // 트랜잭션 시작
                operations.opsForValue().increment((K) originkey, count); // 1증가
                String value = (String) operations.opsForValue().get(originkey); // 값 조회
                log.info("after increment, get(originKey) = {}", value); 
                operations.opsForValue().set((K) newkey, (V) value); // 새로운 키에 값 저장

                return operations.exec(); // 트랜잭션 종료
            }
        });
        return new RedisDto(newkey, (String) result.get(1)); // 두번째 결과값 (값조회)
    }


}

```

- Test의 결과입니다.

 ```java
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
    void incrAndCopy_sessionTest_exception() {

        assertThatThrownBy(() -> sessionCallbackService.incrAndCopy(key, copyKey, 10))
                .isInstanceOf(IllegalArgumentException.class);

        //then
        String value = redisTemplate.opsForValue().get(key);
        assertThat(Integer.parseInt(value)).isEqualTo(1);
    }

}

 ```

- 테스트의 결과 또한 @Transactional 어노테이션을 활용했을 때와 모두 동일합니다.
- 예외 발생시 데이터가 저장되지 않고, 트랜잭션 내부에서 값을 활용하려고하면 예외가 발생합니다.

### 두가지 방식의 장단점

#### @Transactional

- 다른 PlatformTransactionManager의 지원을 받으면 아주 간단하게 구현이 된다는 장점이 있습니다.
- 프록시로 작동하기 때문에 트랜잭션이 전파됩니다.
- 하지만 `watch` 명령어를 사용해서 낙관적 락을 걸 수가 없습니다.

#### SessionCallback

- 트랜잭션을 적용하기 위해서 다른 의존성이 필요로하지 않습니다.
- 낙관적락을 사용할 수 있습니다.
- 코드의 가독성이 좋지않는다는 단점이 있습니다.
- 트랜잭션이 SessionCallback 안에서만 실행되고 종료되기 때문에 트랜잭션의 전파가 이뤄지지 않습니다.

그래서 두가지를 잘 조합해서 활용하는 편이 좋을 것 같습니다.

### 

## 2. LuaScript

### 그렇다면 Redis에서 하나의 단위로 조건을 걸거나 데이터를 조작하는 여러 명령어를 실행하는 방법은 없을까?

- Lua script를 활용하는 방법이 있습니다.
- 이전에 incrAndCopy()와 같은 메서드를 트랜잭션으로 구성했던 것과 동일하게 lua script를 활용합니다.
- Lua script는 레디스 서버 즉, 레디스 실행엔진 내부에 Lua 인터프리터릍 통해서 script를 실행합니다.
- script에서 값을 체크하고 조건 또는 다른 명령어에 활용도 가능합니다.
- 또한 **Atomic**을 보장해주기 때문에 동시성 문제 또한 사라지게 됩니다.
- Redis 서버에서는 script를 Volatile로 캐싱해서 사용하기도 합니다.

### Spring에서의 활용

1. resource 디렉토리에 `.lua` script를 작성합니다.

   ```lua
   -- incr.lua
   redis.call("INCR", KEYS[1])
   
   -- incrAndCopy.lua
   redis.call('INCRBY', KEYS[1], ARGV[1])
   local value = redis.call('GET', KEYS[1])
   redis.call('SET', KEYS[1], value)
   return tonumber(value)
   ```



2. 그리고 해당 스크립트를 Bean으로 등록합니다.

   ```java
   @Bean
   public RedisScript<Long> IncrAndCopyScript() {
     Resource script = new ClassPathResource("scripts/incrAndCopy.lua");
     return RedisScript.of(script, Long.class);
   }
   
   @Bean
   public RedisScript<Void> IncrScript() {
     Resource script = new ClassPathResource("scripts/incr.lua");
     return RedisScript.of(script);
   }
   
   ```

3. RedisScript를 redisTemplate으로 활용합니다.

   ```java
   @Service
   @RequiredArgsConstructor
   public class RedisLuaService implements RedisService {
   
       private final StringRedisTemplate redisTemplate;
       private final RedisScript<Long> incrAndCopyScript;
       private final RedisScript<Void> incrScript;
   
       @Override
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
   
   ```

- 이전 트랜잭션에서 실패했던 incrAndCopy() 메서드의 로직이 정상적으로 작동합니다.
- Script 또한 Transaction 내에서 활용할 수도 있습니다.

```java
@Test
@DisplayName("예외가 발생해도, rollback이 되지 않는다.")
void incr_lua_test_throw_exception() {

  // when
  assertThatThrownBy(() -> luaService.incr(key, true))
    .isInstanceOf(RuntimeException.class);
  // then
  String value = redisTemplate.opsForValue().get(key);
  assertThat(Integer.parseInt(value)).isEqualTo(2);
}

@Test
@DisplayName("예외가 발생하지 않으면, 정상적으로 key의 값이 1 증가한다.")
void incrAndCopy_luaTest() {

  RedisDto redisDto = luaService.incrAndCopy(key, copyKey, 10);

  assertThat(redisDto.getKey()).isEqualTo(copyKey);
  assertThat(Integer.parseInt(redisDto.getValue())).isEqualTo(11);

  //then
  String value = redisTemplate.opsForValue().get(key);
  assertThat(Integer.parseInt(value)).isEqualTo(11);
}

```

- 예외 테스트를 확인해보면 트랜잭션을 사용하지 않기 때문에 예외가 발생해도 데이터는 변경됩니다.
- 이 때, 트랜잭션을 적용하면 데이터의 변경은 생기지 않습니다.
- 그리고 트랜잭션을 적용했을 때 실패했던 로직이 luaScript를 통해서 정상적으로 작동하는 것을 확인할 수 있습니다.

### LuaScript의 활용

- Redis가 싱글스레드로 작동하지만 동시성 문제는 발생할 수 있습니다.
  - 병렬적으로 작동은 하지 않지만 여러 커넥션에서의 요청들이 큐에 담겨서 작동하기 때문에, 순서에 따른 동시성 문제는 발생할 수 있습니다.
- Script는 Redis 서버에서의 Atomic을 보장해주기 때문에 Rate Limit, 선착순 Coupon 발급 또는 Event 참여등에 대한 로직에 활용해서 동시성 문제를 예방할 수 있습니다.



## 3. Pipeline

- Redis 또한 TCP를 이용해서 요청과 응답을 주고 받습니다.
- 그래서 하나의 요청을 보낸다면, 하나의 응답을 반환합니다 (Round Trip Protocol)
  - 이러한 처리가 지속된다면, OS에서 systemcall로 read와 write를 여러차례 반복하게 됩니다.
  - systemcall에 의해서 context switch가 발생하고 잦은 context switch는 부하를 가져오게됩니다.
- 그래서 여러 요청을 한번에 전송하고, 한번에 받도록 지원하는 것이 Pipeline입니다.
- Redis 서버만으로는 pipeline을 지원하지 않습니다.

### Spring에서의 Pipeline

- Spring에서는 Pipeline을 사용할 수 있도록 지원합니다.

![스크린샷 2022-12-13 오후 5.47.33](/Users/cmsskkk/Desktop/스크린샷 2022-12-13 오후 5.47.33.png)

- redisTemplate의 excutePipelined() 메서드를 살펴보면 pipeline을 열고 세션콜백내의 명령어들을 실행하고 결과값 리스트를 반환하는 것을 볼 수 있습니다.
- 이 때, 주의할 것으로 sessionCallback에서의 반환값은 null이 아니면 예외를 반환합니다.
  -  excutePipelined()의 결과값은 pipeLine의 결과값만을 사용할 수 있기 때문에 sessionCallback을 통해서 원하는 결과값을 반환할 수 없습니다.

### 언제 사용하는 것이 좋을까?

- 관계형 DB를 사용하면서 bulk-update, insert를 해야하는 경우와 비슷합니다.
- 다수의 명령어를 실행하고자할 때, 여러번의 요청이 아닌 pipeline을 통해서 성능에 이점을 줄 수 있습니다.



## 4. 결론

- Redis의 트랜잭션의 대한 개념과 Spring을 활용하는 방법에 대해서 공부했습니다.
- 간단한 예시 코드를 통해서 예외상황과 주의해야 할점을 살펴봤지만 단순히 Strings 자료구조만을 활용했다는 것과 실질적으로 적용했을 때의 예상못한 문제를 파악하지못했기에 틀린점이 많을 수도 있습니다.
- `setnx` 명령어를 통한 비관적 락을 활용해서 동시성 문제를 해결하는 방법, LuaScript를 활용해서 동시성 문제를 활용하는 방법에 대해서도 따로 공부해보시면 redis를 이해하고 활용하시는데 많은 도움이 될 것이라고 생각합니다.
- Redis의 장애 요소 및 자료구조, 설정 등등 핵심이되는 내용을 또 공부하고 차후 글을 적어보겠습니다...



### References

- https://redis.io/docs/
- https://developer.redis.com/develop/java/spring/rate-limiting/fixed-window/reactive-lua
- https://docs.spring.io/spring-data-redis/docs/current/reference/html/#redis
- https://github.com/leejohy-0223/proxy
