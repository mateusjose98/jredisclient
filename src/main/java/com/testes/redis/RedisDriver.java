package com.testes.redis;

import java.time.Duration;

interface RedisDriver extends AutoCloseable {

  String get(String key);

  boolean set(String key, String value, Duration ttl);

  boolean delete(String key);

  long incrementBy(String key, long delta);

  @Override
  void close();
}
