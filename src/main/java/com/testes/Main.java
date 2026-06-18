package com.testes;

import com.testes.redis.RedisCacheClient;
import com.testes.redis.RedisClientConfig;
import com.testes.redis.RedisClients;
import com.testes.redis.JedisRedisCacheClientImpl;
import com.testes.redis.RedisSerializer;
import com.testes.redis.RedisSerializers;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Main {

  private Main() {
  }

  public static void main(String[] args) throws InterruptedException {
    String mode = System.getProperty("test.mode", "basic");
    String key = System.getProperty("test.key", "redisclient:demo:key");
    String objectKey = System.getProperty("test.object.key", key + ":object");
    String value = System.getProperty("test.value", "value-" + System.currentTimeMillis());
    String counterKey = System.getProperty("test.counter.key", key + ":counter");
    Duration entryTtl = Duration.ofSeconds(Long.getLong("test.ttl.seconds", 60L));
    long fallbackPauseMs = Long.getLong("test.pause.before.fallback.ms", 8_000L);
    long printerIntervalMs = Long.getLong("test.local.printer.interval.ms", 1_000L);
    boolean printerEnabled = Boolean.parseBoolean(System.getProperty("test.local.printer.enabled", "true"));
    long keepAliveMs = Long.getLong("test.keep.alive.ms", 2_500L);
    RedisSerializer<DemoPayload> demoPayloadSerializer = RedisSerializers.json(DemoPayload.class);

    RedisClientConfig config = RedisClientConfig.builder()
        .host(System.getProperty("redis.host", "localhost"))
        .port(Integer.getInteger("redis.port", 6379))
        .username(emptyToNull(System.getProperty("redis.username")))
        .password(emptyToNull(System.getProperty("redis.password")))
        .database(Integer.getInteger("redis.database", 0))
        .connectionTimeout(Duration.ofMillis(Long.getLong("redis.connect.timeout.ms", 800L)))
        .socketTimeout(Duration.ofMillis(Long.getLong("redis.socket.timeout.ms", 800L)))
        .defaultTtl(Duration.ofSeconds(Long.getLong("redis.default.ttl.seconds", 300L)))
        .localCacheTtl(Duration.ofMillis(Long.getLong("redis.local.ttl.ms", 5_000L)))
        .localCacheMaxSize(Long.getLong("redis.local.max.size", 10_000L))
        .poolMaxTotal(Integer.getInteger("redis.pool.max.total", 64))
        .poolMaxIdle(Integer.getInteger("redis.pool.max.idle", 16))
        .poolMinIdle(Integer.getInteger("redis.pool.min.idle", 4))
        .poolBlockWhenExhausted(Boolean.parseBoolean(System.getProperty("redis.pool.block.when.exhausted", "false")))
        .circuitBreakerEnabled(Boolean.parseBoolean(System.getProperty("redis.cb.enabled", "true")))
        .circuitBreakerFailureThreshold(Integer.getInteger("redis.cb.failure.threshold", 3))
        .circuitBreakerOpenDuration(Duration.ofSeconds(Long.getLong("redis.cb.open.seconds", 15L)))
        .build();

    printHeader(
        mode,
        key,
        objectKey,
        counterKey,
        entryTtl,
        fallbackPauseMs,
        printerEnabled,
        printerIntervalMs,
        keepAliveMs,
        config);

    try (RedisCacheClient client = RedisClients.create(config)) {
      try (LocalCachePrinter printer = LocalCachePrinter.start(client, printerEnabled, printerIntervalMs)) {
        runBasicScenario(client, key, objectKey, value, counterKey, entryTtl, demoPayloadSerializer);

        if ("fallback".equalsIgnoreCase(mode)) {
          runFallbackScenario(client, key, objectKey, fallbackPauseMs, demoPayloadSerializer);
        }

        if (keepAliveMs > 0) {
          System.out.println();
          System.out.println("Mantendo o processo vivo por " + keepAliveMs + " ms para acompanhar o L1...");
          Thread.sleep(keepAliveMs);
        }
      }
    } catch (Exception exception) {
      System.out.println("Main terminou com falha inesperada: " + exception.getMessage());
      exception.printStackTrace(System.out);
    }
  }

  private static void runBasicScenario(
      RedisCacheClient client,
      String key,
      String objectKey,
      String value,
      String counterKey,
      Duration entryTtl,
      RedisSerializer<DemoPayload> demoPayloadSerializer) {

    System.out.println();
    System.out.println("== Basic Scenario ==");

    boolean stored = client.set(key, value, entryTtl);
    System.out.println("SET  key=" + key + " storedInRedis=" + stored);

    String firstRead = client.getOrDefault(key, "<cache-miss>");
    System.out.println("GET  key=" + key + " value=" + firstRead);

    DemoPayload payload = DemoPayload.sample(value);
    boolean objectStored = client.setObject(objectKey, payload, entryTtl, demoPayloadSerializer);
    System.out.println("SETOBJ key=" + objectKey + " storedInRedis=" + objectStored + " value=" + payload);

    DemoPayload objectRead = client.getObjectOrDefault(objectKey, DemoPayload.empty(), demoPayloadSerializer);
    System.out.println("GETOBJ key=" + objectKey + " value=" + objectRead);

    long counterValue = client.increment(counterKey);
    System.out.println("INCR key=" + counterKey + " value=" + counterValue);

    System.out.println("STATUS " + client.status());
  }

  private static void runFallbackScenario(
      RedisCacheClient client,
      String key,
      String objectKey,
      long fallbackPauseMs,
      RedisSerializer<DemoPayload> demoPayloadSerializer)
      throws InterruptedException {

    System.out.println();
    System.out.println("== Fallback Scenario ==");
    System.out.println("1. O valor ja foi aquecido no L1 pela leitura anterior.");
    System.out.println("2. Derrube o Redis agora, se quiser validar o fallback local.");
    System.out.println("3. Aguardando " + fallbackPauseMs + " ms antes da nova leitura...");

    Thread.sleep(fallbackPauseMs);

    String fallbackRead = client.getOrDefault(key, "<cache-miss>");
    System.out.println("GET after pause key=" + key + " value=" + fallbackRead);
    DemoPayload fallbackObject = client.getObjectOrDefault(objectKey, DemoPayload.empty(), demoPayloadSerializer);
    System.out.println("GETOBJ after pause key=" + objectKey + " value=" + fallbackObject);
    System.out.println("STATUS " + client.status());
  }

  private static void printHeader(
      String mode,
      String key,
      String objectKey,
      String counterKey,
      Duration entryTtl,
      long fallbackPauseMs,
      boolean printerEnabled,
      long printerIntervalMs,
      long keepAliveMs,
      RedisClientConfig config) {

    System.out.println("== Redis Client Manual Test ==");
    System.out.println("mode=" + mode);
    System.out.println("redis=" + config.getHost() + ":" + config.getPort() + " db=" + config.getDatabase());
    System.out.println("key=" + key);
    System.out.println("objectKey=" + objectKey);
    System.out.println("counterKey=" + counterKey);
    System.out.println("entryTtl=" + entryTtl);
    System.out.println("localCacheTtl=" + config.getLocalCacheTtl());
    System.out.println("localCacheMaxSize=" + config.getLocalCacheMaxSize());
    System.out.println("pool.maxTotal=" + config.newPoolConfig().getMaxTotal());
    System.out.println("pool.maxIdle=" + config.newPoolConfig().getMaxIdle());
    System.out.println("pool.minIdle=" + config.newPoolConfig().getMinIdle());
    System.out.println("circuitBreaker.enabled=" + config.isCircuitBreakerEnabled());
    System.out.println("circuitBreaker.failureThreshold=" + config.getCircuitBreakerFailureThreshold());
    System.out.println("circuitBreaker.openDuration=" + config.getCircuitBreakerOpenDuration());
    System.out.println("localPrinter.enabled=" + printerEnabled);
    System.out.println("localPrinter.intervalMs=" + printerIntervalMs);
    System.out.println("keepAliveMs=" + keepAliveMs);
    if ("fallback".equalsIgnoreCase(mode)) {
      System.out.println("pauseBeforeFallbackMs=" + fallbackPauseMs);
    }
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static final class LocalCachePrinter implements AutoCloseable {
    private final JedisRedisCacheClientImpl client;
    private final long intervalMs;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    private LocalCachePrinter(JedisRedisCacheClientImpl client, long intervalMs) {
      this.client = client;
      this.intervalMs = intervalMs;
      this.thread = new Thread(this::runLoop, "local-cache-printer");
      this.thread.setDaemon(true);
      this.thread.start();
    }

    static LocalCachePrinter start(RedisCacheClient client, boolean enabled, long intervalMs) {
      if (!enabled || intervalMs <= 0 || !(client instanceof JedisRedisCacheClientImpl)) {
        return new LocalCachePrinter();
      }
      return new LocalCachePrinter((JedisRedisCacheClientImpl) client, intervalMs);
    }

    private LocalCachePrinter() {
      this.client = null;
      this.intervalMs = 0L;
      this.thread = null;
      this.running.set(false);
    }

    private void runLoop() {
      while (running.get()) {
        printSnapshot();
        try {
          Thread.sleep(intervalMs);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    private void printSnapshot() {
      Map<String, Object> snapshot = client.localCacheSnapshot();
      System.out.println("[L1] size=" + snapshot.size() + " values=" + snapshot);
    }

    @Override
    public void close() {
      if (thread == null) {
        return;
      }
      running.set(false);
      thread.interrupt();
      try {
        thread.join(1_500L);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static final class DemoPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private String productId;
    private int quantity;
    private String sourceValue;
    private LocalDate businessDate;
    private LocalDateTime generatedAt;
    private Date legacyDate;

    public DemoPayload() {
    }

    private DemoPayload(
        String productId,
        int quantity,
        String sourceValue,
        LocalDate businessDate,
        LocalDateTime generatedAt,
        Date legacyDate) {
      this.productId = productId;
      this.quantity = quantity;
      this.sourceValue = sourceValue;
      this.businessDate = businessDate;
      this.generatedAt = generatedAt;
      this.legacyDate = legacyDate;
    }

    private static DemoPayload empty() {
      return new DemoPayload(
          "empty",
          0,
          "default",
          LocalDate.of(2000, 1, 1),
          LocalDateTime.of(2000, 1, 1, 0, 0),
          Date.from(LocalDateTime.of(2000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC)));
    }

    private static DemoPayload sample(String sourceValue) {
      LocalDateTime generatedAt = LocalDateTime.now().withNano(0);
      return new DemoPayload(
          "produto-123",
          42,
          sourceValue,
          LocalDate.now(),
          generatedAt,
          Date.from(generatedAt.toInstant(ZoneOffset.UTC)));
    }

    public String getProductId() {
      return productId;
    }

    public void setProductId(String productId) {
      this.productId = productId;
    }

    public int getQuantity() {
      return quantity;
    }

    public void setQuantity(int quantity) {
      this.quantity = quantity;
    }

    public String getSourceValue() {
      return sourceValue;
    }

    public void setSourceValue(String sourceValue) {
      this.sourceValue = sourceValue;
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

    public Date getLegacyDate() {
      return legacyDate;
    }

    public void setLegacyDate(Date legacyDate) {
      this.legacyDate = legacyDate;
    }

    @Override
    public String toString() {
      return "DemoPayload{"
          + "productId='" + productId + '\''
          + ", quantity=" + quantity
          + ", sourceValue='" + sourceValue + '\''
          + ", businessDate=" + businessDate
          + ", generatedAt=" + generatedAt
          + ", legacyDate=" + legacyDate
          + '}';
    }
  }
}
