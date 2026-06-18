package com.testes.redis;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JedisRedisCacheClientImpl implements RedisCacheClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(JedisRedisCacheClientImpl.class);
  private static final RedisSerializer<String> STRING_SERIALIZER = RedisSerializers.string();

  private final RedisDriver redisDriver;
  private final Cache<String, LocalCacheEntry> localCache;
  private final Duration defaultTtl;
  private final boolean circuitBreakerEnabled;
  private final CircuitBreaker circuitBreaker;
  private final RedisFailureLogger failureLogger;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public JedisRedisCacheClientImpl(RedisClientConfig config) {
    this(config, new JedisPoolRedisDriver(config));
  }

  JedisRedisCacheClientImpl(RedisClientConfig config, RedisDriver redisDriver) {
    Objects.requireNonNull(config, "config");
    this.redisDriver = Objects.requireNonNull(redisDriver, "redisDriver");
    this.defaultTtl = config.getDefaultTtl();
    this.localCache = Caffeine.newBuilder()
        .maximumSize(config.getLocalCacheMaxSize())
        .expireAfterWrite(config.getLocalCacheTtl())
        .build();
    this.circuitBreakerEnabled = config.isCircuitBreakerEnabled();
    this.circuitBreaker = circuitBreakerEnabled
        ? new CircuitBreaker(config.getCircuitBreakerFailureThreshold(), config.getCircuitBreakerOpenDuration())
        : null;
    this.failureLogger = new RedisFailureLogger(LOGGER, config.getFailureLogInterval().toMillis());
  }

  @Override
  public Optional<String> get(String key) {
    return readValue(key, STRING_SERIALIZER, "GET");
  }

  @Override
  public String getOrDefault(String key, String defaultValue) {
    return get(key).orElse(defaultValue);
  }

  @Override
  public <T extends Serializable> Optional<T> getObject(String key, Class<T> type) {
    return getObject(key, RedisSerializers.java(type));
  }

  @Override
  public <T> Optional<T> getObject(String key, RedisSerializer<T> serializer) {
    return readValue(key, serializer, "GET_OBJECT");
  }

  @Override
  public <T extends Serializable> T getObjectOrDefault(String key, T defaultValue, Class<T> type) {
    return getObject(key, type).orElse(defaultValue);
  }

  @Override
  public <T> T getObjectOrDefault(String key, T defaultValue, RedisSerializer<T> serializer) {
    return getObject(key, serializer).orElse(defaultValue);
  }

  @Override
  public boolean set(String key, String value) {
    return set(key, value, defaultTtl);
  }

  @Override
  public boolean set(String key, String value, Duration ttl) {
    return writeValue(key, value, ttl, STRING_SERIALIZER, "SET");
  }

  @Override
  public <T extends Serializable> boolean setObject(String key, T value) {
    return setObject(key, value, defaultTtl);
  }

  @Override
  public <T extends Serializable> boolean setObject(String key, T value, Duration ttl) {
    return writeValue(key, value, ttl, javaSerializerForValue(value), "SET_OBJECT");
  }

  @Override
  public <T> boolean setObject(String key, T value, RedisSerializer<T> serializer) {
    return setObject(key, value, defaultTtl, serializer);
  }

  @Override
  public <T> boolean setObject(String key, T value, Duration ttl, RedisSerializer<T> serializer) {
    return writeValue(key, value, ttl, serializer, "SET_OBJECT");
  }

  @Override
  public boolean delete(String key) {
    if (!isOpenForUsage() || !hasText(key)) {
      return false;
    }

    localCache.invalidate(key);

    if (!isRedisCallPermitted("DEL", key)) {
      return false;
    }

    try {
      boolean deleted = redisDriver.delete(key);
      recordSuccess();
      return deleted;
    } catch (Exception exception) {
      recordFailure("DEL", key, exception);
      return false;
    }
  }

  @Override
  public long increment(String key) {
    return incrementBy(key, 1L);
  }

  @Override
  public long incrementBy(String key, long delta) {
    if (!isOpenForUsage() || !hasText(key)) {
      return 0L;
    }

    localCache.invalidate(key);

    if (!isRedisCallPermitted("INCRBY", key)) {
      return 0L;
    }

    try {
      long value = redisDriver.incrementBy(key, delta);
      recordSuccess();
      localCache.put(key, new LocalCacheEntry(Long.toString(value), Long.toString(value)));
      return value;
    } catch (Exception exception) {
      recordFailure("INCRBY", key, exception);
      return 0L;
    }
  }

  @Override
  public void invalidateLocal(String key) {
    if (!hasText(key)) {
      return;
    }
    localCache.invalidate(key);
  }

  @Override
  public void clearLocalCache() {
    localCache.invalidateAll();
  }

  @Override
  public RedisClientStatus status() {
    return new RedisClientStatus(currentCircuitBreakerState(), localCache.estimatedSize(), closed.get());
  }

  public Map<String, Object> localCacheSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    for (Map.Entry<String, LocalCacheEntry> entry : localCache.asMap().entrySet()) {
      snapshot.put(entry.getKey(), entry.getValue().snapshotValue());
    }
    return Collections.unmodifiableMap(snapshot);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    localCache.invalidateAll();
    try {
      redisDriver.close();
    } catch (Exception exception) {
      LOGGER.warn("redis status=close-failure message={}", exception.getMessage());
    }
  }

  private boolean isOpenForUsage() {
    return !closed.get();
  }

  private boolean isRedisCallPermitted(String operation, String key) {
    if (!circuitBreakerEnabled) {
      return true;
    }
    boolean permitted = circuitBreaker.tryAcquirePermission();
    if (!permitted) {
      failureLogger.logFastFail(operation, key, currentCircuitBreakerState());
    }
    return permitted;
  }

  private void recordSuccess() {
    if (circuitBreakerEnabled) {
      circuitBreaker.recordSuccess();
    }
    failureLogger.logRecovery(currentCircuitBreakerState());
  }

  private void recordFailure(String operation, String key, Exception exception) {
    if (circuitBreakerEnabled) {
      circuitBreaker.recordFailure();
    }
    failureLogger.logFailure(operation, key, currentCircuitBreakerState(), exception);
  }

  private String currentCircuitBreakerState() {
    return circuitBreakerEnabled ? circuitBreaker.getState().name() : "DISABLED";
  }

  private Duration resolveTtl(Duration ttl) {
    Duration effectiveTtl = ttl == null ? defaultTtl : ttl;
    if (effectiveTtl.isNegative() || effectiveTtl.isZero()) {
      return null;
    }
    return effectiveTtl;
  }

  private <T> Optional<T> readValue(String key, RedisSerializer<T> serializer, String operation) {
    if (!isOpenForUsage() || !hasText(key) || serializer == null) {
      return Optional.empty();
    }

    Optional<T> localValue = readFromLocalCache(key, serializer, operation);
    if (localValue != null) {
      return localValue;
    }

    if (!isRedisCallPermitted(operation, key)) {
      return Optional.empty();
    }

    try {
      String payload = redisDriver.get(key);
      recordSuccess();
      if (payload == null) {
        localCache.invalidate(key);
        return Optional.empty();
      }

      T decodedValue = deserializeValue(serializer, payload, operation, key);
      if (decodedValue == null) {
        localCache.invalidate(key);
        return Optional.empty();
      }

      localCache.put(key, new LocalCacheEntry(payload, decodedValue));
      return Optional.of(decodedValue);
    } catch (Exception exception) {
      recordFailure(operation, key, exception);
      return Optional.empty();
    }
  }

  private <T> Optional<T> readFromLocalCache(String key, RedisSerializer<T> serializer, String operation) {
    LocalCacheEntry entry = localCache.getIfPresent(key);
    if (entry == null) {
      return null;
    }

    if (entry.cachedValue() != null && serializer.targetType().isInstance(entry.cachedValue())) {
      return Optional.of(serializer.targetType().cast(entry.cachedValue()));
    }

    T decodedValue = deserializeValue(serializer, entry.payload(), operation + "_L1", key);
    if (decodedValue == null) {
      localCache.invalidate(key);
      return null;
    }

    localCache.put(key, new LocalCacheEntry(entry.payload(), decodedValue));
    return Optional.of(decodedValue);
  }

  private <T> boolean writeValue(String key, T value, Duration ttl, RedisSerializer<T> serializer, String operation) {
    if (!isOpenForUsage() || !hasText(key) || value == null || serializer == null) {
      return false;
    }

    Duration effectiveTtl = resolveTtl(ttl);
    if (effectiveTtl == null) {
      return false;
    }

    String payload = serializeValue(serializer, value, operation, key);
    if (payload == null) {
      return false;
    }

    localCache.put(key, new LocalCacheEntry(payload, value));

    if (!isRedisCallPermitted(operation, key)) {
      return false;
    }

    try {
      boolean stored = redisDriver.set(key, payload, effectiveTtl);
      recordSuccess();
      return stored;
    } catch (Exception exception) {
      recordFailure(operation, key, exception);
      return false;
    }
  }

  private <T> String serializeValue(RedisSerializer<T> serializer, T value, String operation, String key) {
    try {
      return serializer.serialize(value);
    } catch (Exception exception) {
      logSerializationFailure(operation, key, exception);
      return null;
    }
  }

  private <T> T deserializeValue(RedisSerializer<T> serializer, String payload, String operation, String key) {
    try {
      return serializer.deserialize(payload);
    } catch (Exception exception) {
      logSerializationFailure(operation, key, exception);
      return null;
    }
  }

  private void logSerializationFailure(String operation, String key, Exception exception) {
    LOGGER.warn(
        "redis operation={} key={} status=serialization-failure errorType={} message={}",
        operation,
        key,
        exception.getClass().getSimpleName(),
        exception.getMessage());

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("redis serialization failure operation={} key={}", operation, key, exception);
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Serializable> RedisSerializer<T> javaSerializerForValue(T value) {
    return RedisSerializers.java((Class<T>) value.getClass());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static final class LocalCacheEntry {
    private final String payload;
    private final Object cachedValue;

    private LocalCacheEntry(String payload, Object cachedValue) {
      this.payload = payload;
      this.cachedValue = cachedValue;
    }

    private String payload() {
      return payload;
    }

    private Object cachedValue() {
      return cachedValue;
    }

    private Object snapshotValue() {
      return cachedValue != null ? cachedValue : payload;
    }
  }
}
