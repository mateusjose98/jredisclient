package com.testes.redis;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JedisRedisCacheClientImplTest {

  @Test
  void shouldServeValueFromLocalCacheWhenRedisBecomesUnavailable() {
    TestRedisDriver driver = new TestRedisDriver();
    driver.store.put("produto:1", "cached");

    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());

    Optional<String> firstRead = client.get("produto:1");
    driver.failReads = true;
    Optional<String> secondRead = client.get("produto:1");

    assertTrue(firstRead.isPresent());
    assertEquals("cached", firstRead.get());
    assertTrue(secondRead.isPresent());
    assertEquals("cached", secondRead.get());
    assertEquals(1, driver.getCalls.get());
  }

  @Test
  void shouldOpenCircuitBreakerAndFastFailAfterFailureThreshold() {
    TestRedisDriver driver = new TestRedisDriver();
    driver.failReads = true;

    RedisClientConfig config = baseConfigBuilder()
        .circuitBreakerFailureThreshold(1)
        .circuitBreakerOpenDuration(Duration.ofMinutes(1))
        .build();

    JedisRedisCacheClientImpl client = new JedisRedisCacheClientImpl(config, driver);

    assertTrue(client.get("produto:2").isEmpty());
    assertTrue(client.get("produto:2").isEmpty());
    assertEquals(1, driver.getCalls.get());
    assertEquals("OPEN", client.status().getCircuitBreakerState());
  }

  @Test
  void shouldKeepLocalValueWhenRemoteSetFails() {
    TestRedisDriver driver = new TestRedisDriver();
    driver.failWrites = true;

    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());

    boolean stored = client.set("produto:3", "fallback-local", Duration.ofMinutes(1));
    Optional<String> readAfterFailedSet = client.get("produto:3");

    assertFalse(stored);
    assertTrue(readAfterFailedSet.isPresent());
    assertEquals("fallback-local", readAfterFailedSet.get());
    assertEquals(0, driver.getCalls.get());
  }

  @Test
  void shouldExpireLocalCacheEntries() throws InterruptedException {
    TestRedisDriver driver = new TestRedisDriver();
    driver.store.put("produto:4", "valor-temporario");

    RedisClientConfig config = baseConfigBuilder()
        .localCacheTtl(Duration.ofMillis(80))
        .circuitBreakerEnabled(false)
        .build();

    JedisRedisCacheClientImpl client = new JedisRedisCacheClientImpl(config, driver);

    assertEquals("valor-temporario", client.getOrDefault("produto:4", "miss"));

    driver.failReads = true;
    Thread.sleep(160);

    assertEquals("miss", client.getOrDefault("produto:4", "miss"));
    assertEquals(2, driver.getCalls.get());
  }

  @Test
  void shouldStoreAndReadSerializableObjectUsingDefaultSerializer() {
    TestRedisDriver driver = new TestRedisDriver();
    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());
    TestPayload payload = new TestPayload("produto:5", 3);

    boolean stored = client.setObject("produto:5", payload, Duration.ofMinutes(1));
    Optional<TestPayload> firstRead = client.getObject("produto:5", TestPayload.class);

    driver.failReads = true;
    Optional<TestPayload> fallbackRead = client.getObject("produto:5", TestPayload.class);

    assertTrue(stored);
    assertTrue(firstRead.isPresent());
    assertEquals(payload, firstRead.get());
    assertTrue(fallbackRead.isPresent());
    assertEquals(payload, fallbackRead.get());
    assertEquals(0, driver.getCalls.get());
  }

  @Test
  void shouldSupportCustomSerializerForNonSerializableObject() {
    TestRedisDriver driver = new TestRedisDriver();
    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());
    CustomPayload payload = new CustomPayload("cliente", 9);
    RedisSerializer<CustomPayload> serializer = new CustomPayloadSerializer();

    boolean stored = client.setObject("produto:6", payload, Duration.ofMinutes(1), serializer);
    Optional<CustomPayload> read = client.getObject("produto:6", serializer);

    assertTrue(stored);
    assertTrue(read.isPresent());
    assertEquals(payload, read.get());
  }

  @Test
  void shouldSupportJsonSerializerWithDates() {
    TestRedisDriver driver = new TestRedisDriver();
    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());
    RedisSerializer<DatedPayload> serializer = RedisSerializers.json(DatedPayload.class);
    LocalDateTime generatedAt = LocalDateTime.of(2026, 6, 17, 20, 55, 0);
    DatedPayload payload = new DatedPayload(
        "pedido-1",
        LocalDate.of(2026, 6, 17),
        generatedAt,
        Date.from(generatedAt.toInstant(ZoneOffset.UTC)));

    boolean stored = client.setObject("pedido:1", payload, Duration.ofMinutes(1), serializer);
    Optional<DatedPayload> read = client.getObject("pedido:1", serializer);

    driver.failReads = true;
    Optional<DatedPayload> fallbackRead = client.getObject("pedido:1", serializer);

    assertTrue(stored);
    assertTrue(read.isPresent());
    assertEquals(payload, read.get());
    assertTrue(fallbackRead.isPresent());
    assertEquals(payload, fallbackRead.get());
    assertEquals(0, driver.getCalls.get());
  }

  @Test
  void shouldInvalidateLocalEntryWhenDeleteFails() {
    TestRedisDriver driver = new TestRedisDriver();
    driver.store.put("produto:delete", "valor");

    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());
    assertEquals("valor", client.getOrDefault("produto:delete", "miss"));

    driver.failDeletes = true;
    driver.failReads = true;

    assertFalse(client.delete("produto:delete"));
    assertEquals("miss", client.getOrDefault("produto:delete", "miss"));
  }

  @Test
  void shouldReturnZeroWhenIncrementFails() {
    TestRedisDriver driver = new TestRedisDriver();
    driver.failIncrements = true;

    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());

    assertEquals(0L, client.incrementBy("counter", 5));
  }

  @Test
  void shouldExposeObjectInLocalSnapshot() {
    TestRedisDriver driver = new TestRedisDriver();
    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());
    TestPayload payload = new TestPayload("snapshot", 10);

    assertTrue(client.setObject("snapshot:key", payload, Duration.ofMinutes(1)));
    assertEquals(payload, client.localCacheSnapshot().get("snapshot:key"));
  }

  @Test
  void shouldReturnFalseWhenSerializerFailsWithoutCallingRedis() {
    TestRedisDriver driver = new TestRedisDriver();
    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());
    AtomicBoolean serializeCalled = new AtomicBoolean(false);
    RedisSerializer<CustomPayload> brokenSerializer = new RedisSerializer<CustomPayload>() {
      @Override
      public Class<CustomPayload> targetType() {
        return CustomPayload.class;
      }

      @Override
      public String serialize(CustomPayload value) {
        serializeCalled.set(true);
        throw new RedisSerializationException("boom");
      }

      @Override
      public CustomPayload deserialize(String payload) {
        return null;
      }
    };

    assertFalse(client.setObject("broken:key", new CustomPayload("broken", 1), Duration.ofMinutes(1), brokenSerializer));
    assertTrue(serializeCalled.get());
    assertEquals(0, driver.setCalls.get());
    assertNull(client.localCacheSnapshot().get("broken:key"));
  }

  @Test
  void shouldCloseIdempotently() {
    TestRedisDriver driver = new TestRedisDriver();
    JedisRedisCacheClientImpl client = newClient(driver, baseConfigBuilder());

    client.close();
    client.close();

    assertTrue(client.status().isClosed());
  }

  private JedisRedisCacheClientImpl newClient(TestRedisDriver driver, RedisClientConfig.Builder builder) {
    return new JedisRedisCacheClientImpl(builder.build(), driver);
  }

  private RedisClientConfig.Builder baseConfigBuilder() {
    return RedisClientConfig.builder()
        .defaultTtl(Duration.ofMinutes(5))
        .localCacheTtl(Duration.ofSeconds(5))
        .localCacheMaxSize(128)
        .failureLogInterval(Duration.ofMillis(1));
  }

  private static final class TestPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String key;
    private final int version;

    private TestPayload(String key, int version) {
      this.key = key;
      this.version = version;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof TestPayload)) {
        return false;
      }
      TestPayload payload = (TestPayload) other;
      return version == payload.version && key.equals(payload.key);
    }

    @Override
    public int hashCode() {
      return key.hashCode() * 31 + version;
    }
  }

  private static final class CustomPayload {
    private final String name;
    private final int amount;

    private CustomPayload(String name, int amount) {
      this.name = name;
      this.amount = amount;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof CustomPayload)) {
        return false;
      }
      CustomPayload payload = (CustomPayload) other;
      return amount == payload.amount && name.equals(payload.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode() * 31 + amount;
    }
  }

  private static final class CustomPayloadSerializer implements RedisSerializer<CustomPayload> {

    @Override
    public Class<CustomPayload> targetType() {
      return CustomPayload.class;
    }

    @Override
    public String serialize(CustomPayload value) {
      return value.name + "|" + value.amount;
    }

    @Override
    public CustomPayload deserialize(String payload) {
      String[] parts = payload.split("\\|", 2);
      return new CustomPayload(parts[0], Integer.parseInt(parts[1]));
    }
  }

  private static final class DatedPayload {
    private String id;
    private LocalDate businessDate;
    private LocalDateTime generatedAt;
    private Date legacyDate;

    public DatedPayload() {
    }

    private DatedPayload(String id, LocalDate businessDate, LocalDateTime generatedAt, Date legacyDate) {
      this.id = id;
      this.businessDate = businessDate;
      this.generatedAt = generatedAt;
      this.legacyDate = legacyDate;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
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
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof DatedPayload)) {
        return false;
      }
      DatedPayload payload = (DatedPayload) other;
      return id.equals(payload.id)
          && businessDate.equals(payload.businessDate)
          && generatedAt.equals(payload.generatedAt)
          && legacyDate.equals(payload.legacyDate);
    }

    @Override
    public int hashCode() {
      int result = id.hashCode();
      result = 31 * result + businessDate.hashCode();
      result = 31 * result + generatedAt.hashCode();
      result = 31 * result + legacyDate.hashCode();
      return result;
    }
  }
}
