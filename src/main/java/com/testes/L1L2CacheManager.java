package com.testes;

import java.util.Optional;

import com.testes.l1.InMemoryCacheApi;
import com.testes.l2.RedisCacheApi;

public class L1L2CacheManager implements AutoCloseable {

  private final InMemoryCacheApi inMemoryCacheApi;
  private final RedisCacheApi redisCacheApi;

  // Cliente quer usar ambos os caches
  public L1L2CacheManager(InMemoryCacheApi inMemoryCacheApi, RedisCacheApi redisCacheApi) {
    this.inMemoryCacheApi = inMemoryCacheApi;
    this.redisCacheApi = redisCacheApi;
  }

  // Cliente quer usar apenas o cache em memória
  public L1L2CacheManager(InMemoryCacheApi inMemoryCacheApi) {
    this(inMemoryCacheApi, null);
  }

  // Cliente não quer usar o cache
  public L1L2CacheManager() {
    this(null, null);
  }

  public <K, V> void put(K key, V value, int ttlSeconds) {
    if (inMemoryCacheApi != null) {
      inMemoryCacheApi.put(key, value);
    }
    if (redisCacheApi != null) {
      redisCacheApi.put(key, value, ttlSeconds);
    }
  }

  public <K, V> Optional<V> get(K key, Class<V> clazz) {
    if (inMemoryCacheApi != null) {
      Optional<V> value = inMemoryCacheApi.get(key, clazz);
      if (value.isPresent()) {
        return value;
      }
    }
    if (redisCacheApi != null) {
      Optional<V> value = redisCacheApi.get(key, clazz);
      if (value.isPresent()) {
        if (inMemoryCacheApi != null) {
          inMemoryCacheApi.put(key, value.get());
        }

        return value;
      }
    }
    return Optional.empty();
  }

  public <K> void remove(K key) {
    if (inMemoryCacheApi != null) {
      inMemoryCacheApi.remove(key);
    }
    if (redisCacheApi != null) {
      redisCacheApi.remove(key);
    }
  }

  @Override
  public void close() {
    if (inMemoryCacheApi != null) {
      inMemoryCacheApi.close();
    }
    if (redisCacheApi != null) {
      redisCacheApi.close();
    }
  }

}
