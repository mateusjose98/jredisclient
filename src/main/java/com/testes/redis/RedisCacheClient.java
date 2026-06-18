package com.testes.redis;

import java.io.Serializable;
import java.time.Duration;
import java.util.Optional;

public interface RedisCacheClient extends AutoCloseable {

  Optional<String> get(String key);

  String getOrDefault(String key, String defaultValue);

  <T extends Serializable> Optional<T> getObject(String key, Class<T> type);

  <T> Optional<T> getObject(String key, RedisSerializer<T> serializer);

  <T extends Serializable> T getObjectOrDefault(String key, T defaultValue, Class<T> type);

  <T> T getObjectOrDefault(String key, T defaultValue, RedisSerializer<T> serializer);

  boolean set(String key, String value);

  boolean set(String key, String value, Duration ttl);

  <T extends Serializable> boolean setObject(String key, T value);

  <T extends Serializable> boolean setObject(String key, T value, Duration ttl);

  <T> boolean setObject(String key, T value, RedisSerializer<T> serializer);

  <T> boolean setObject(String key, T value, Duration ttl, RedisSerializer<T> serializer);

  boolean delete(String key);

  long increment(String key);

  long incrementBy(String key, long delta);

  void invalidateLocal(String key);

  void clearLocalCache();

  RedisClientStatus status();

  @Override
  void close();
}
