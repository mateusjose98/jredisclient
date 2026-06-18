package com.testes.redis;

public interface RedisSerializer<T> {

  Class<T> targetType();

  String serialize(T value);

  T deserialize(String payload);
}
