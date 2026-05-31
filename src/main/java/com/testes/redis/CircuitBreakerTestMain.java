package com.testes.redis;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CircuitBreakerTestMain {

  private CircuitBreakerTestMain() {
  }

  public static void main(String[] args) throws InterruptedException {
    String redisHost = System.getProperty("redis.host", "localhost");
    int redisPort = Integer.getInteger("redis.port", 6379);
    boolean testFailure = Boolean.parseBoolean(System.getProperty("test.failure", "true"));

    System.out.println("=== Redis Circuit Breaker Test ===");
    System.out.println("redis.host=" + redisHost);
    System.out.println("redis.port=" + redisPort);
    System.out.println("test.failure=" + testFailure);
    System.out.println();

    RedisClientConfig config = RedisClientConfig.builder()
        .host(redisHost)
        .port(redisPort)
        .defaultTtl(Duration.ofMinutes(1))
        .circuitBreakerEnabled(true)
        .circuitBreakerFailureThreshold(3)
        .circuitBreakerResetTimeoutMs(10_000)
        .healthCheckEnabled(false)
        .build();

    try (JedisRedisCacheClientImpl redis = (JedisRedisCacheClientImpl) RedisClients.create(config)) {

      // Fase 1: Operacoes normais por 5 segundos
      System.out.println("Phase 1: Normal operations (5s)");
      runOperations(redis, 5, 1, "OK");

      if (testFailure) {
        // Fase 2: Forcar falha usando host invalido
        System.out.println("\nPhase 2: Forcing failure with invalid host (10s)");
        RedisClientConfig badConfig = RedisClientConfig.builder()
            .host("invalid-redis-host-that-does-not-exist-12345")
            .port(6379)
            .defaultTtl(Duration.ofMinutes(1))
            .circuitBreakerEnabled(true)
            .circuitBreakerFailureThreshold(3)
            .circuitBreakerResetTimeoutMs(10_000)
            .connectionTimeout(Duration.ofMillis(500))
            .socketTimeout(Duration.ofMillis(500))
            .healthCheckEnabled(false)
            .build();

        try (JedisRedisCacheClientImpl badRedis = (JedisRedisCacheClientImpl) RedisClients.create(badConfig)) {
          runOperations(badRedis, 10, 1, "FAIL");
        }

        // Fase 3: Aguardar reset do circuit breaker
        System.out.println("\nPhase 3: Waiting for circuit breaker reset (15s)");
        Thread.sleep(15_000);

        // Fase 4: Tentar reconectar (volta ao host valido)
        System.out.println("\nPhase 4: Attempting reconnection (5s)");
        runOperations(redis, 5, 1, "RECONNECT");
      }

      System.out.println("\n=== Test Complete ===");
      System.out.println("Final status: " + redis.getStatus());
    }
  }

  private static void runOperations(JedisRedisCacheClientImpl redis, int durationSeconds, int intervalSeconds,
                                    String phase) throws InterruptedException {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger opCount = new AtomicInteger(0);

    // Task 1: Faz operacoes constantemente
    scheduler.scheduleAtFixedRate(() -> {
      try {
        String key = "test:circuit:" + phase;
        boolean setResult = redis.set(key, "value-" + System.currentTimeMillis(), Duration.ofSeconds(30));
        String getResult = redis.getOrDefault(key, "FALLBACK");
        opCount.incrementAndGet();
        System.out.printf("[%s] op=%d set=%b get=%s\n", phase, opCount.get(), setResult, getResult);
      } catch (Exception e) {
        System.out.printf("[%s] ERROR: %s\n", phase, e.getMessage());
      }
    }, 0, intervalSeconds, TimeUnit.SECONDS);

    // Task 2: Monitora estado do circuit breaker
    scheduler.scheduleAtFixedRate(() -> {
      System.out.printf("[%s] status=%s\n", phase, redis.getStatus());
    }, 0, 2, TimeUnit.SECONDS);

    // Aguarda duracao
    Thread.sleep(durationSeconds * 1_000L);
    scheduler.shutdown();
    scheduler.awaitTermination(5, TimeUnit.SECONDS);
  }
}
