package com.testes.redis;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.Objects;

final class JedisPoolRedisDriver implements RedisDriver {

  private final JedisPool jedisPool;

  JedisPoolRedisDriver(RedisClientConfig config) {
    Objects.requireNonNull(config, "config");
    this.jedisPool = new JedisPool(
        config.newPoolConfig(),
        new HostAndPort(config.getHost(), config.getPort()),
        DefaultJedisClientConfig.builder()
            .user(config.getUsername())
            .password(config.getPassword())
            .database(config.getDatabase())
            .connectionTimeoutMillis((int) config.getConnectionTimeout().toMillis())
            .socketTimeoutMillis((int) config.getSocketTimeout().toMillis())
            .build());
  }

  @Override
  public String get(String key) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.get(key);
    }
  }

  @Override
  public boolean set(String key, String value, Duration ttl) {
    try (Jedis jedis = jedisPool.getResource()) {
      return "OK".equals(jedis.psetex(key, ttl.toMillis(), value));
    }
  }

  @Override
  public boolean delete(String key) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.del(key) > 0;
    }
  }

  @Override
  public long incrementBy(String key, long delta) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.incrBy(key, delta);
    }
  }

  @Override
  public void close() {
    jedisPool.close();
  }
}
