package com.testes.redis;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class CircuitBreaker {

  enum State {
    CLOSED, OPEN, HALF_OPEN
  }

  private final int failureThreshold;
  private final long resetTimeoutMs;
  private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
  private final AtomicInteger failureCount = new AtomicInteger(0);
  private final AtomicLong lastFailureTimeMs = new AtomicLong(0);

  public CircuitBreaker(int failureThreshold, long resetTimeoutMs) {
    this.failureThreshold = failureThreshold;
    this.resetTimeoutMs = resetTimeoutMs;
  }

  public <T> T call(CheckedSupplier<T> supplier, T fallback) throws Exception {
    State currentState = state.get();

    if (currentState == State.OPEN) {
      if (System.currentTimeMillis() - lastFailureTimeMs.get() > resetTimeoutMs) {
        state.set(State.HALF_OPEN);
        failureCount.set(0);
      } else {
        return fallback;
      }
    }

    try {
      T result = supplier.get();
      onSuccess();
      return result;
    } catch (Exception exception) {
      onFailure();
      if (currentState == State.HALF_OPEN) {
        state.set(State.OPEN);
        throw exception;
      }
      throw exception;
    }
  }

  private void onSuccess() {
    failureCount.set(0);
    state.set(State.CLOSED);
  }

  private void onFailure() {
    lastFailureTimeMs.set(System.currentTimeMillis());
    int failures = failureCount.incrementAndGet();
    if (failures >= failureThreshold) {
      state.set(State.OPEN);
    }
  }

  public boolean isOpen() {
    return state.get() == State.OPEN;
  }

  public String getState() {
    return state.get().name();
  }

  @FunctionalInterface
  public interface CheckedSupplier<T> {
    T get() throws Exception;
  }
}
