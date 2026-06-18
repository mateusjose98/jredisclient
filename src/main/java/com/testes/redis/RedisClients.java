package com.testes.redis;

public final class RedisClients {

  private RedisClients() {
  }

  public static RedisCacheClient create(RedisClientConfig config) {
    return new JedisRedisCacheClientImpl(config);
  }
}
