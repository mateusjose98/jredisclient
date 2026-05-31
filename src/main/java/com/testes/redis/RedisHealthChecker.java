package com.testes.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RedisHealthChecker {

  private static final Logger LOGGER = LoggerFactory.getLogger(RedisHealthChecker.class);

  private final JedisPool jedisPool;
  private final Duration checkInterval;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean healthy = new AtomicBoolean(true);

  public RedisHealthChecker(JedisPool jedisPool, Duration checkInterval) {
    this.jedisPool = jedisPool;
    this.checkInterval = checkInterval;
    this.scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
      Thread t = new Thread(runnable, "RedisHealthChecker");
      t.setDaemon(true);
      return t;
    });
  }

  public void start() {
    scheduler.scheduleAtFixedRate(
        this::checkHealth,
        checkInterval.toMillis(),
        checkInterval.toMillis(),
        TimeUnit.MILLISECONDS);
    LOGGER.info("redis.health-checker started interval-ms={}", checkInterval.toMillis());
  }

  public void stop() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
    LOGGER.info("redis.health-checker stopped");
  }

  public boolean isHealthy() {
    return healthy.get();
  }

  private void checkHealth() {
    try (Jedis jedis = jedisPool.getResource()) {
      String pong = jedis.ping();
      if ("PONG".equals(pong)) {
        healthy.set(true);
        LOGGER.debug("redis.health-check status=healthy");
      } else {
        healthy.set(false);
        LOGGER.warn("redis.health-check status=unhealthy response={}", pong);
      }
    } catch (Exception exception) {
      healthy.set(false);
      LOGGER.warn("redis.health-check status=unhealthy error={}", exception.getMessage());
    }
  }
}
