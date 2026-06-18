package com.testes.redis;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisFailureLoggerTest {

  @Test
  void shouldRateLimitFailureLogsAndEmitRecovery() throws InterruptedException {
    CountingLogger countingLogger = new CountingLogger();
    RedisFailureLogger logger = new RedisFailureLogger(countingLogger.logger(), 20);

    logger.logFailure("GET", "key", "OPEN", new IllegalStateException("down"));
    logger.logFailure("GET", "key", "OPEN", new IllegalStateException("down"));
    logger.logFastFail("GET", "key", "OPEN");

    assertEquals(1, countingLogger.warnCount.get());

    Thread.sleep(30);
    logger.logFastFail("GET", "key", "OPEN");
    assertEquals(2, countingLogger.warnCount.get());

    logger.logRecovery("CLOSED");
    logger.logRecovery("CLOSED");
    assertEquals(1, countingLogger.infoCount.get());
  }

  private static final class CountingLogger {
    private final AtomicInteger warnCount = new AtomicInteger();
    private final AtomicInteger infoCount = new AtomicInteger();
    private final AtomicInteger debugCount = new AtomicInteger();

    private Logger logger() {
      return (Logger) Proxy.newProxyInstance(
          Logger.class.getClassLoader(),
          new Class<?>[]{Logger.class},
          (proxy, method, args) -> {
            switch (method.getName()) {
              case "warn":
                warnCount.incrementAndGet();
                return null;
              case "info":
                infoCount.incrementAndGet();
                return null;
              case "debug":
                debugCount.incrementAndGet();
                return null;
              case "isDebugEnabled":
                return false;
              default:
                if (method.getReturnType().equals(boolean.class)) {
                  return false;
                }
                return null;
            }
          });
    }
  }
}
