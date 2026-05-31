package com.testes.redis;

import java.io.Closeable;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

public interface RedisCacheClient extends Closeable {

  Optional<String> get(String key);

  String getOrDefault(String key, String defaultValue);

  boolean set(String key, String value);

  boolean set(String key, String value, Duration ttl);

  boolean delete(String key);

  long increment(String key);

  long incrementBy(String key, long delta);

  <T> T execute(String operationName, String key, Function<redis.clients.jedis.Jedis, T> callback, T fallback);

  @Override
  void close();
}