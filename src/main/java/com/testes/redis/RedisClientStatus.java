package com.testes.redis;

public final class RedisClientStatus {

  private final String circuitBreakerState;
  private final long localCacheEstimatedSize;
  private final boolean closed;

  public RedisClientStatus(String circuitBreakerState, long localCacheEstimatedSize, boolean closed) {
    this.circuitBreakerState = circuitBreakerState;
    this.localCacheEstimatedSize = localCacheEstimatedSize;
    this.closed = closed;
  }

  public String getCircuitBreakerState() {
    return circuitBreakerState;
  }

  public long getLocalCacheEstimatedSize() {
    return localCacheEstimatedSize;
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public String toString() {
    return "RedisClientStatus{"
        + "circuitBreakerState='" + circuitBreakerState + '\''
        + ", localCacheEstimatedSize=" + localCacheEstimatedSize
        + ", closed=" + closed
        + '}';
  }
}
