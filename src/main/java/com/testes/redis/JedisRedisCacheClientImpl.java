package com.testes.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class JedisRedisCacheClientImpl implements RedisCacheClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(JedisRedisCacheClientImpl.class);

  private final JedisPool jedisPool;
  private final Duration defaultTtl;
  private final CircuitBreaker circuitBreaker;
  private final RedisHealthChecker healthChecker;
  private final boolean circuitBreakerEnabled;

  public JedisRedisCacheClientImpl(RedisClientConfig config) {
    Objects.requireNonNull(config, "config");
    this.defaultTtl = config.getDefaultTtl();
    this.jedisPool = new JedisPool(
        config.getPoolConfig(),
        new HostAndPort(config.getHost(), config.getPort()),
        DefaultJedisClientConfig.builder()
            .user(config.getUsername())
            .password(config.getPassword())
            .database(config.getDatabase())
            .connectionTimeoutMillis((int) config.getConnectionTimeout().toMillis())
            .socketTimeoutMillis((int) config.getSocketTimeout().toMillis())
            .build());

    this.circuitBreakerEnabled = config.isCircuitBreakerEnabled();
    this.circuitBreaker = circuitBreakerEnabled
        ? new CircuitBreaker(config.getCircuitBreakerFailureThreshold(), config.getCircuitBreakerResetTimeoutMs())
        : null;

    this.healthChecker = config.isHealthCheckEnabled()
        ? new RedisHealthChecker(jedisPool, config.getHealthCheckInterval())
        : null;

    if (healthChecker != null) {
      healthChecker.start();
    }

    LOGGER.info("redis.client initialized circuitBreaker={} healthCheck={}", circuitBreakerEnabled,
        healthChecker != null);
  }

  @Override
  public Optional<String> get(String key) {
    return Optional.ofNullable(execute("GET", key, jedis -> jedis.get(key), null));
  }

  @Override
  public String getOrDefault(String key, String defaultValue) {
    return execute("GET", key, jedis -> {
      String value = jedis.get(key);
      return value != null ? value : defaultValue;
    }, defaultValue);
  }

  @Override
  public boolean set(String key, String value) {
    return set(key, value, defaultTtl);
  }

  @Override
  public boolean set(String key, String value, Duration ttl) {
    Duration effectiveTtl = ttl == null ? defaultTtl : ttl;
    return execute("SET", key, jedis -> "OK".equals(jedis.psetex(key, effectiveTtl.toMillis(), value)), false);
  }

  @Override
  public boolean delete(String key) {
    return execute("DEL", key, jedis -> jedis.del(key) > 0, false);
  }

  @Override
  public long increment(String key) {
    return incrementBy(key, 1L);
  }

  @Override
  public long incrementBy(String key, long delta) {
    return execute("INCRBY", key, jedis -> jedis.incrBy(key, delta), 0L);
  }

  @Override
  public <T> T execute(String operationName, String key, Function<Jedis, T> callback, T fallback) {
    long start = System.nanoTime();

    if (circuitBreakerEnabled && circuitBreaker.isOpen()) {
      LOGGER.warn("redis.operation={} key={} status=circuit-open cb-state={}", operationName, key,
          circuitBreaker.getState());
      return fallback;
    }

    try {
      T result = circuitBreakerEnabled
          ? circuitBreaker.call(() -> executeWithJedis(callback), null)
          : executeWithJedis(callback);

      if (result == null && !circuitBreakerEnabled) {
        return executeWithJedis(callback);
      }

      logSuccess(operationName, key, start);
      return result != null ? result : fallback;
    } catch (Exception exception) {
      logFailure(operationName, key, start, exception);
      return fallback;
    }
  }

  private <T> T executeWithJedis(Function<Jedis, T> callback) {
    try (Jedis jedis = jedisPool.getResource()) {
      return callback.apply(jedis);
    }
  }

  @Override
  public void close() {
    if (healthChecker != null) {
      healthChecker.stop();
    }
    jedisPool.close();
    LOGGER.info("redis.client closed");
  }

  public String getStatus() {
    return "circuitBreaker=" + (circuitBreakerEnabled ? circuitBreaker.getState() : "disabled")
        + " health=" + (healthChecker != null ? (healthChecker.isHealthy() ? "ok" : "unhealthy") : "unchecked");
  }

  private void logSuccess(String operationName, String key, long start) {
    LOGGER.info("redis.operation={} key={} elapsedMs={} status=success",
        operationName,
        key,
        elapsedMillis(start));
  }

  private void logFailure(String operationName, String key, long start, Exception exception) {
    LOGGER.warn("redis.operation={} key={} elapsedMs={} status=failure message={}",
        operationName,
        key,
        elapsedMillis(start),
        exception.getMessage());
    LOGGER.debug("redis.operation={} key={} failure-stack", operationName, key, exception);
  }

  private long elapsedMillis(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }
}