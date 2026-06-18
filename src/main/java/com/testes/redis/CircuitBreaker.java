package com.testes.redis;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class CircuitBreaker {

  public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final int failureThreshold;
  private final long openIntervalMs;
  private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
  private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
  private final AtomicLong openUntilMs = new AtomicLong(0);
  private final AtomicBoolean halfOpenProbeInFlight = new AtomicBoolean(false);

  public CircuitBreaker(int failureThreshold, Duration openInterval) {
    this.failureThreshold = failureThreshold;
    this.openIntervalMs = openInterval.toMillis();
  }

  public boolean tryAcquirePermission() {
    while (true) {
      State current = state.get();
      if (current == State.CLOSED) {
        return true;
      }
      if (current == State.OPEN) {
        long now = System.currentTimeMillis();
        if (now < openUntilMs.get()) {
          return false;
        }
        if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
          halfOpenProbeInFlight.set(false);
        }
        continue;
      }
      return halfOpenProbeInFlight.compareAndSet(false, true);
    }
  }

  public void recordSuccess() {
    consecutiveFailures.set(0);
    openUntilMs.set(0);
    halfOpenProbeInFlight.set(false);
    state.set(State.CLOSED);
  }

  public void recordFailure() {
    State current = state.get();
    halfOpenProbeInFlight.set(false);
    if (current == State.HALF_OPEN) {
      openCircuit();
      return;
    }

    int failures = consecutiveFailures.incrementAndGet();
    if (failures >= failureThreshold) {
      openCircuit();
    }
  }

  public State getState() {
    return state.get();
  }

  private void openCircuit() {
    consecutiveFailures.set(0);
    openUntilMs.set(System.currentTimeMillis() + openIntervalMs);
    state.set(State.OPEN);
  }
}
