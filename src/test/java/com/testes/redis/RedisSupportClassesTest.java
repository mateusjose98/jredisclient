package com.testes.redis;

import com.testes.Main;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisSupportClassesTest {

  @Test
  void shouldDescribeClientStatus() {
    RedisClientStatus status = new RedisClientStatus("OPEN", 12, true);

    assertEquals("OPEN", status.getCircuitBreakerState());
    assertEquals(12, status.getLocalCacheEstimatedSize());
    assertTrue(status.isClosed());
    assertTrue(status.toString().contains("OPEN"));
  }

  @Test
  void shouldCreateAndCloseClientFromFactory() {
    RedisClientConfig config = RedisClientConfig.builder()
        .host("127.0.0.1")
        .port(1)
        .connectionTimeout(Duration.ofMillis(20))
        .socketTimeout(Duration.ofMillis(20))
        .build();

    RedisCacheClient client = RedisClients.create(config);

    assertInstanceOf(JedisRedisCacheClientImpl.class, client);
    assertFalse(client.status().isClosed());
    client.close();
    assertTrue(client.status().isClosed());
  }

  @Test
  void shouldRunMainWithoutPropagatingRedisFailure() {
    String previousHost = System.getProperty("redis.host");
    String previousPort = System.getProperty("redis.port");
    String previousConnectTimeout = System.getProperty("redis.connect.timeout.ms");
    String previousSocketTimeout = System.getProperty("redis.socket.timeout.ms");
    String previousKeepAlive = System.getProperty("test.keep.alive.ms");
    String previousPrinter = System.getProperty("test.local.printer.enabled");

    try {
      System.setProperty("redis.host", "127.0.0.1");
      System.setProperty("redis.port", "1");
      System.setProperty("redis.connect.timeout.ms", "20");
      System.setProperty("redis.socket.timeout.ms", "20");
      System.setProperty("test.keep.alive.ms", "0");
      System.setProperty("test.local.printer.enabled", "false");

      assertTimeout(Duration.ofSeconds(3), () -> Main.main(new String[0]));
    } finally {
      restoreProperty("redis.host", previousHost);
      restoreProperty("redis.port", previousPort);
      restoreProperty("redis.connect.timeout.ms", previousConnectTimeout);
      restoreProperty("redis.socket.timeout.ms", previousSocketTimeout);
      restoreProperty("test.keep.alive.ms", previousKeepAlive);
      restoreProperty("test.local.printer.enabled", previousPrinter);
    }
  }

  private void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
