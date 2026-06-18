package com.testes.redis;

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class RedisFailureLogger {

  private final Logger logger;
  private final long intervalMs;
  private final AtomicLong nextLogAtMs = new AtomicLong(0);
  private final AtomicBoolean failureMode = new AtomicBoolean(false);

  RedisFailureLogger(Logger logger, long intervalMs) {
    this.logger = logger;
    this.intervalMs = intervalMs;
  }

  void logFailure(String operation, String key, String circuitBreakerState, Exception exception) {
    failureMode.set(true);
    if (!shouldLogNow()) {
      return;
    }

    logger.warn(
        "redis operation={} key={} status=failure breaker={} errorType={} message={}",
        operation,
        key,
        circuitBreakerState,
        exception.getClass().getSimpleName(),
        exception.getMessage());

    if (logger.isDebugEnabled()) {
      logger.debug("redis failure operation={} key={}", operation, key, exception);
    }
  }

  void logFastFail(String operation, String key, String circuitBreakerState) {
    failureMode.set(true);
    if (!shouldLogNow()) {
      return;
    }

    logger.warn("redis operation={} key={} status=fast-fail breaker={}", operation, key, circuitBreakerState);
  }

  void logRecovery(String circuitBreakerState) {
    if (failureMode.compareAndSet(true, false)) {
      logger.info("redis status=recovered breaker={}", circuitBreakerState);
    }
  }

  private boolean shouldLogNow() {
    long now = System.currentTimeMillis();
    long next = nextLogAtMs.get();
    if (now < next) {
      return false;
    }
    return nextLogAtMs.compareAndSet(next, now + intervalMs);
  }
}
