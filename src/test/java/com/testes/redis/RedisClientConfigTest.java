package com.testes.redis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisClientConfigTest {

  @Test
  void shouldBuildWithCustomSettingsAndClonePoolConfig() {
    GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
    poolConfig.setMaxTotal(200);
    poolConfig.setMaxIdle(40);
    poolConfig.setMinIdle(10);
    poolConfig.setBlockWhenExhausted(true);

    RedisClientConfig config = RedisClientConfig.builder()
        .host("redis.internal")
        .port(6380)
        .username("app")
        .password("secret")
        .database(2)
        .connectionTimeout(Duration.ofMillis(250))
        .socketTimeout(Duration.ofMillis(500))
        .defaultTtl(Duration.ofMinutes(1))
        .localCacheTtl(Duration.ofSeconds(3))
        .localCacheMaxSize(321)
        .poolConfig(poolConfig)
        .circuitBreakerEnabled(false)
        .circuitBreakerFailureThreshold(7)
        .circuitBreakerOpenDuration(Duration.ofSeconds(4))
        .failureLogInterval(Duration.ofSeconds(9))
        .build();

    GenericObjectPoolConfig<Jedis> cloneA = config.newPoolConfig();
    GenericObjectPoolConfig<Jedis> cloneB = config.newPoolConfig();

    assertEquals("redis.internal", config.getHost());
    assertEquals(6380, config.getPort());
    assertEquals("app", config.getUsername());
    assertEquals("secret", config.getPassword());
    assertEquals(2, config.getDatabase());
    assertEquals(Duration.ofMillis(250), config.getConnectionTimeout());
    assertEquals(Duration.ofMillis(500), config.getSocketTimeout());
    assertEquals(Duration.ofMinutes(1), config.getDefaultTtl());
    assertEquals(Duration.ofSeconds(3), config.getLocalCacheTtl());
    assertEquals(321, config.getLocalCacheMaxSize());
    assertFalse(config.isCircuitBreakerEnabled());
    assertEquals(7, config.getCircuitBreakerFailureThreshold());
    assertEquals(Duration.ofSeconds(4), config.getCircuitBreakerOpenDuration());
    assertEquals(Duration.ofSeconds(9), config.getFailureLogInterval());
    assertEquals(200, cloneA.getMaxTotal());
    assertEquals(40, cloneA.getMaxIdle());
    assertEquals(10, cloneA.getMinIdle());
    assertTrue(cloneA.getBlockWhenExhausted());
    assertNotSame(cloneA, cloneB);
  }

  @Test
  void shouldKeepInternalPoolConfigImmutableFromReturnedCopies() {
    RedisClientConfig config = RedisClientConfig.builder().build();

    GenericObjectPoolConfig<Jedis> firstCopy = config.newPoolConfig();
    firstCopy.setMaxTotal(999);

    GenericObjectPoolConfig<Jedis> secondCopy = config.newPoolConfig();

    assertEquals(64, secondCopy.getMaxTotal());
  }

  @Test
  void shouldRejectInvalidBuilderArguments() {
    assertThrows(IllegalArgumentException.class, () -> RedisClientConfig.builder().host(" ").build());
    assertThrows(IllegalArgumentException.class, () -> RedisClientConfig.builder().port(0).build());
    assertThrows(IllegalArgumentException.class, () -> RedisClientConfig.builder().database(-1).build());
    assertThrows(IllegalArgumentException.class,
        () -> RedisClientConfig.builder().connectionTimeout(Duration.ZERO).build());
    assertThrows(IllegalArgumentException.class,
        () -> RedisClientConfig.builder().socketTimeout(Duration.ZERO).build());
    assertThrows(IllegalArgumentException.class,
        () -> RedisClientConfig.builder().defaultTtl(Duration.ZERO).build());
    assertThrows(IllegalArgumentException.class,
        () -> RedisClientConfig.builder().localCacheTtl(Duration.ZERO).build());
    assertThrows(IllegalArgumentException.class,
        () -> RedisClientConfig.builder().localCacheMaxSize(0).build());
    assertThrows(IllegalArgumentException.class,
        () -> RedisClientConfig.builder().circuitBreakerFailureThreshold(0).build());
    assertThrows(IllegalArgumentException.class,
        () -> RedisClientConfig.builder().circuitBreakerOpenDuration(Duration.ZERO).build());
    assertThrows(IllegalArgumentException.class,
        () -> RedisClientConfig.builder().failureLogInterval(Duration.ZERO).build());
  }
}
