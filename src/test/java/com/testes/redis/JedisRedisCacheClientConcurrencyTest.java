package com.testes.redis;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JedisRedisCacheClientConcurrencyTest {

  @Test
  void shouldServeConcurrentWarmReadsFromLocalCacheOnly() throws Exception {
    TestRedisDriver driver = new TestRedisDriver();
    driver.store.put("produto:concurrente", "valor");

    JedisRedisCacheClientImpl client = newClient(driver);
    assertEquals("valor", client.getOrDefault("produto:concurrente", "miss"));
    driver.failReads = true;

    int threads = 16;
    int iterations = 1_500;

    assertTimeout(Duration.ofSeconds(5), () -> {
      ExecutorService executor = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<String>> futures = new ArrayList<>();

      try {
        for (int i = 0; i < threads; i++) {
          futures.add(executor.submit(() -> {
            start.await();
            String last = null;
            for (int j = 0; j < iterations; j++) {
              last = client.getOrDefault("produto:concurrente", "miss");
            }
            return last;
          }));
        }

        start.countDown();

        for (Future<String> future : futures) {
          assertEquals("valor", future.get(5, TimeUnit.SECONDS));
        }
      } finally {
        executor.shutdownNow();
      }
    });

    assertEquals(1, driver.getCalls.get());
  }

  @Test
  void shouldSupportConcurrentObjectReadsAndWrites() throws Exception {
    TestRedisDriver driver = new TestRedisDriver();
    JedisRedisCacheClientImpl client = newClient(driver);
    RedisSerializer<ConcurrentPayload> serializer = RedisSerializers.json(ConcurrentPayload.class);

    int threads = 8;
    int iterations = 500;

    assertTimeout(Duration.ofSeconds(5), () -> {
      ExecutorService executor = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Boolean>> futures = new ArrayList<>();

      try {
        for (int i = 0; i < threads; i++) {
          final int threadId = i;
          futures.add(executor.submit(() -> {
            start.await();
            for (int j = 0; j < iterations; j++) {
              String key = "obj:" + threadId;
              ConcurrentPayload payload = new ConcurrentPayload("id-" + threadId, j);
              if (!client.setObject(key, payload, Duration.ofMinutes(1), serializer)) {
                return false;
              }
              ConcurrentPayload loaded = client.getObjectOrDefault(key, null, serializer);
              if (loaded == null || loaded.version != j || !loaded.id.equals("id-" + threadId)) {
                return false;
              }
            }
            return true;
          }));
        }

        start.countDown();

        for (Future<Boolean> future : futures) {
          assertTrue(future.get(5, TimeUnit.SECONDS));
        }
      } finally {
        executor.shutdownNow();
      }
    });

    Map<String, Object> snapshot = client.localCacheSnapshot();
    assertEquals(threads, snapshot.size());
  }

  @Test
  void shouldCompleteHighVolumeLocalReadsQuickly() {
    TestRedisDriver driver = new TestRedisDriver();
    driver.store.put("perf:key", "valor");

    JedisRedisCacheClientImpl client = newClient(driver);
    assertEquals("valor", client.getOrDefault("perf:key", "miss"));
    driver.failReads = true;

    assertTimeout(Duration.ofSeconds(2), () -> {
      for (int i = 0; i < 50_000; i++) {
        assertEquals("valor", client.getOrDefault("perf:key", "miss"));
      }
    });

    assertEquals(1, driver.getCalls.get());
  }

  private JedisRedisCacheClientImpl newClient(TestRedisDriver driver) {
    RedisClientConfig config = RedisClientConfig.builder()
        .defaultTtl(Duration.ofMinutes(5))
        .localCacheTtl(Duration.ofMinutes(1))
        .localCacheMaxSize(10_000)
        .failureLogInterval(Duration.ofMillis(1))
        .build();
    return new JedisRedisCacheClientImpl(config, driver);
  }

  private static final class ConcurrentPayload {
    private String id;
    private int version;
    private LocalDate businessDate;
    private LocalDateTime generatedAt;

    public ConcurrentPayload() {
    }

    private ConcurrentPayload(String id, int version) {
      this.id = id;
      this.version = version;
      this.businessDate = LocalDate.of(2026, 6, 17);
      this.generatedAt = LocalDateTime.of(2026, 6, 17, 21, 30, 0);
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public int getVersion() {
      return version;
    }

    public void setVersion(int version) {
      this.version = version;
    }

    public LocalDate getBusinessDate() {
      return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
      this.businessDate = businessDate;
    }

    public LocalDateTime getGeneratedAt() {
      return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
      this.generatedAt = generatedAt;
    }
  }
}
