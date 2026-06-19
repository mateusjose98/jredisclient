package com.testes.l1;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class InMemoryCacheApi {

  private final Cache<Object, Object> cache;

  public InMemoryCacheApi(ConfiguracaoCacheMemoria configuracao) {
    Objects.requireNonNull(configuracao, "Configuração de cache não pode ser nula.");
    this.cache = Caffeine.newBuilder()
        .maximumSize(configuracao.getTamanhoMaximo())
        .expireAfterWrite(Duration.ofSeconds(configuracao.getTtlSegundos()))
        .build();
  }

  public <K, V> Optional<V> get(K key, Class<V> type) {
    Object value = cache.getIfPresent(key);
    if (value == null) {
      return Optional.empty();
    }
    if (!type.isInstance(value)) {
      return Optional.empty();
    }
    return Optional.of(type.cast(value));
  }

  public <K, V> void put(K key, V value) {
    cache.put(key, value);
  }

  public <K> void remove(K key) {
    cache.invalidate(key);
  }

  public void close() {
    cache.cleanUp();
  }

}
