package com.testes.redis;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

final class TestRedisDriver implements RedisDriver {

  final Map<String, String> store = new ConcurrentHashMap<>();
  final AtomicInteger getCalls = new AtomicInteger();
  final AtomicInteger setCalls = new AtomicInteger();
  final AtomicInteger deleteCalls = new AtomicInteger();
  final AtomicInteger incrementCalls = new AtomicInteger();
  volatile boolean failReads;
  volatile boolean failWrites;
  volatile boolean failDeletes;
  volatile boolean failIncrements;
  volatile long readDelayMs;
  volatile long writeDelayMs;
  volatile long deleteDelayMs;
  volatile long incrementDelayMs;

  @Override
  public String get(String key) {
    getCalls.incrementAndGet();
    delay(readDelayMs);
    if (failReads) {
      throw new IllegalStateException("redis-down-read");
    }
    return store.get(key);
  }

  @Override
  public boolean set(String key, String value, Duration ttl) {
    setCalls.incrementAndGet();
    delay(writeDelayMs);
    if (failWrites) {
      throw new IllegalStateException("redis-down-write");
    }
    store.put(key, value);
    return true;
  }

  @Override
  public boolean delete(String key) {
    deleteCalls.incrementAndGet();
    delay(deleteDelayMs);
    if (failDeletes) {
      throw new IllegalStateException("redis-down-delete");
    }
    return store.remove(key) != null;
  }

  @Override
  public long incrementBy(String key, long delta) {
    incrementCalls.incrementAndGet();
    delay(incrementDelayMs);
    if (failIncrements) {
      throw new IllegalStateException("redis-down-increment");
    }
    long current = store.containsKey(key) ? Long.parseLong(store.get(key)) : 0L;
    long updated = current + delta;
    store.put(key, Long.toString(updated));
    return updated;
  }

  @Override
  public void close() {
  }

  private void delay(long delayMs) {
    if (delayMs <= 0) {
      return;
    }
    LockSupport.parkNanos(Duration.ofMillis(delayMs).toNanos());
  }
}
