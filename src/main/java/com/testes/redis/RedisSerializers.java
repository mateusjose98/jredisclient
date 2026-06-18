package com.testes.redis;

import java.io.Serializable;

public final class RedisSerializers {

  private static final RedisSerializer<String> STRING = new RedisSerializer<String>() {
    @Override
    public Class<String> targetType() {
      return String.class;
    }

    @Override
    public String serialize(String value) {
      return value;
    }

    @Override
    public String deserialize(String payload) {
      return payload;
    }
  };

  private RedisSerializers() {
  }

  public static RedisSerializer<String> string() {
    return STRING;
  }

  public static <T> RedisSerializer<T> json(Class<T> type) {
    return new JsonRedisSerializer<>(type);
  }

  public static <T extends Serializable> RedisSerializer<T> java(Class<T> type) {
    return new JavaRedisSerializer<>(type);
  }
}
