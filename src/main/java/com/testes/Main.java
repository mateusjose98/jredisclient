package com.testes;

import com.testes.redis.RedisCacheClient;
import com.testes.redis.RedisClientConfig;
import com.testes.redis.RedisClients;

import java.time.Duration;

public class Main {
    public static void main(String[] args) {
        RedisClientConfig config = RedisClientConfig.builder()
                .host(System.getProperty("redis.host", "localhost"))
                .port(Integer.getInteger("redis.port", 6379))
                .defaultTtl(Duration.ofMinutes(5))
                .build();

        try (RedisCacheClient cacheClient = RedisClients.create(config)) {
            cacheClient.set("health-check", "ok", Duration.ofSeconds(30));
            String cachedValue = cacheClient.getOrDefault("health-check", "cache-unavailable");
            System.out.println("Redis cache value: " + cachedValue);
        }
    }
}