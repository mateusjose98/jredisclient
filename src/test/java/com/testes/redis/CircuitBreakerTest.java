package com.testes.redis;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

  @Test
  void shouldOpenAfterFailureThreshold() {
    CircuitBreaker circuitBreaker = new CircuitBreaker(2, Duration.ofSeconds(1));

    assertTrue(circuitBreaker.tryAcquirePermission());
    circuitBreaker.recordFailure();
    assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

    circuitBreaker.recordFailure();

    assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    assertFalse(circuitBreaker.tryAcquirePermission());
  }

  @Test
  void shouldCloseAfterHalfOpenSuccess() throws InterruptedException {
    CircuitBreaker circuitBreaker = new CircuitBreaker(1, Duration.ofMillis(20));
    circuitBreaker.recordFailure();

    Thread.sleep(40);

    assertTrue(circuitBreaker.tryAcquirePermission());
    assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

    circuitBreaker.recordSuccess();

    assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    assertTrue(circuitBreaker.tryAcquirePermission());
  }

  @Test
  void shouldReopenWhenHalfOpenProbeFails() throws InterruptedException {
    CircuitBreaker circuitBreaker = new CircuitBreaker(1, Duration.ofMillis(20));
    circuitBreaker.recordFailure();

    Thread.sleep(40);

    assertTrue(circuitBreaker.tryAcquirePermission());
    circuitBreaker.recordFailure();

    assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    assertFalse(circuitBreaker.tryAcquirePermission());
  }

  @Test
  void shouldAllowOnlySingleHalfOpenProbeUnderConcurrency() throws Exception {
    CircuitBreaker circuitBreaker = new CircuitBreaker(1, Duration.ofMillis(30));
    circuitBreaker.recordFailure();
    Thread.sleep(50);

    int threads = 12;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();

    try {
      for (int i = 0; i < threads; i++) {
        futures.add(executor.submit(() -> {
          start.await();
          return circuitBreaker.tryAcquirePermission();
        }));
      }

      start.countDown();

      int granted = 0;
      for (Future<Boolean> future : futures) {
        if (future.get()) {
          granted++;
        }
      }

      assertEquals(1, granted);
      assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    } finally {
      executor.shutdownNow();
    }
  }
}
