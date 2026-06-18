package com.testes.redis;

public final class RedisSerializationException extends RuntimeException {

  public RedisSerializationException(String message, Throwable cause) {
    super(message, cause);
  }

  public RedisSerializationException(String message) {
    super(message);
  }
}
