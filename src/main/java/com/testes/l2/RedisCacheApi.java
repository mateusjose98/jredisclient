package com.testes.l2;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisCacheApi implements AutoCloseable {

  private final ConfiguracaoRedis configuracao;
  private final JedisPool jedisPool;
  private final JacksonCacheSerializer serializer;

  private static final Logger LOGGER = Logger.getLogger(RedisCacheApi.class.getName());

  public RedisCacheApi(ConfiguracaoRedis configuracao) {
    this(configuracao, new JacksonCacheSerializer());
  }

  public RedisCacheApi(ConfiguracaoRedis configuracao, JacksonCacheSerializer serializer) {
    this.configuracao = Objects.requireNonNull(configuracao, "Configuração nula não é aceita.");
    this.serializer = Objects.requireNonNull(serializer, "Serializer nulo não é aceito.");
    this.jedisPool = construirPool(configuracao);
    ping();
  }

  private JedisPool construirPool(ConfiguracaoRedis configuracao) {
    JedisPoolConfig poolConfig = new JedisPoolConfig();

    poolConfig.setMaxTotal(configuracao.getMaxConnections());
    poolConfig.setMaxIdle(configuracao.getMaxIdleConnections());
    poolConfig.setMinIdle(configuracao.getMinIdleConnections());
    poolConfig.setBlockWhenExhausted(configuracao.isBlockWhenExhausted());
    poolConfig.setMaxWait(Duration.ofMillis(configuracao.getMaxWaitMillis()));

    return new JedisPool(
        poolConfig,
        configuracao.getHost(),
        configuracao.getPort(),
        configuracao.getTimeout(),
        configuracao.getPassword(),
        configuracao.getDatabase());
  }

  private void ping() {
    if (poolIndisponivel()) {
      return;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      String response = jedis.ping();

      if (!"PONG".equalsIgnoreCase(response)) {
        logAviso("Resposta inesperada do Redis ao ping: " + response);
        return;
      }

      logAviso("Ping no Redis bem-sucedido. Resposta: " + response);
    } catch (Exception e) {
      logErro("Erro ao realizar ping no Redis.", e);
    }
  }

  public <K, V> Optional<V> get(K key, Class<V> type) {
    if (key == null) {
      logAviso("GET ignorado. Key Redis inválida.");
      return Optional.empty();
    }

    if (type == null) {
      logAviso("GET ignorado. Type nulo. key=" + key);
      return Optional.empty();
    }

    if (poolIndisponivel()) {
      return Optional.empty();
    }

    try (Jedis jedis = jedisPool.getResource()) {
      byte[] value = jedis.get(toRedisKeyBytes(key));

      if (value == null) {
        return Optional.empty();
      }

      return Optional.ofNullable(serializer.deserialize(value, type));
    } catch (Exception e) {
      logErro("Erro ao buscar valor no Redis. key=" + key + ", type=" + type.getName(), e);
      return Optional.empty();
    }
  }

  public <K, V> void put(K key, V value) {

    if (value == null) {
      logAviso("SET ignorado. Value null. key=" + key);
      return;
    }

    if (poolIndisponivel()) {
      return;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.set(toRedisKeyBytes(key), serializer.serialize(value));
    } catch (Exception e) {
      logErro("Erro ao salvar valor no Redis. key=" + key + ", valueType=" + value.getClass().getName(), e);
    }
  }

  public <K, V> void put(K key, V value, int ttlSeconds) {
    if (value == null) {
      logAviso("SETEX ignorado. Value null. key=" + key);
      return;
    }

    if (ttlSeconds <= 0) {
      logAviso("SETEX ignorado. TTL inválido. key=" + key + ", ttlSeconds=" + ttlSeconds);
      return;
    }

    if (poolIndisponivel()) {
      return;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.setex(toRedisKeyBytes(key), ttlSeconds, serializer.serialize(value));
    } catch (Exception e) {
      logErro("Erro ao salvar valor com TTL no Redis. key=" + key + ", ttlSeconds=" + ttlSeconds
          + ", valueType=" + value.getClass().getName(), e);
    }
  }

  public <K> boolean exists(K key) {
    if (poolIndisponivel()) {
      return false;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.exists(toRedisKeyBytes(key));
    } catch (Exception e) {
      logErro("Erro ao verificar existência no Redis. key=" + key, e);
      return false;
    }
  }

  public <K> long remove(K key) {
    if (key == null) {
      logAviso("DEL ignorado. Key Redis inválida.");
      return 0L;
    }

    if (poolIndisponivel()) {
      return 0L;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.del(toRedisKeyBytes(key));
    } catch (Exception e) {
      logErro("Erro ao remover valor do Redis. key=" + key, e);
      return 0L;
    }
  }

  public <K> long ttl(K key) {
    if (key == null) {
      logAviso("TTL ignorado. Key Redis inválida.");
      return -2L;
    }

    if (poolIndisponivel()) {
      return -2L;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.ttl(toRedisKeyBytes(key));
    } catch (Exception e) {
      logErro("Erro ao consultar TTL no Redis. key=" + key, e);
      return -2L;
    }
  }

  public <K> void expire(K key, int ttlSeconds) {
    if (key == null) {
      logAviso("EXPIRE ignorado. Key Redis inválida.");
      return;
    }

    if (ttlSeconds <= 0) {
      logAviso("EXPIRE ignorado. TTL inválido. key=" + key + ", ttlSeconds=" + ttlSeconds);
      return;
    }

    if (poolIndisponivel()) {
      return;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      jedis.expire(toRedisKeyBytes(key), ttlSeconds);
    } catch (Exception e) {
      logErro("Erro ao aplicar expire no Redis. key=" + key + ", ttlSeconds=" + ttlSeconds, e);
    }
  }

  @Override
  public void close() {
    if (jedisPool == null) {
      return;
    }

    try {
      jedisPool.close();
    } catch (Exception e) {
      logErro("Erro ao fechar pool Redis.", e);
    }
  }

  private byte[] toRedisKeyBytes(Object key) {
    return toRedisKey(key).getBytes(StandardCharsets.UTF_8);
  }

  private String toRedisKey(Object key) {
    String ambiente = configuracao.getAmbiente();

    if (ambiente == null || ambiente.trim().isEmpty()) {
      return key.toString();
    }

    return ambiente + ":" + key.toString();
  }

  private boolean isKeyInvalida(String key) {
    return key == null || key.trim().isEmpty();
  }

  private void logAviso(String mensagem) {
    LOGGER.warning(mensagem);
  }

  private void logErro(String mensagem, Exception e) {
    LOGGER.log(Level.WARNING, mensagem, e);
  }

  private boolean poolIndisponivel() {
    if (jedisPool == null) {
      logAviso("Pool Redis indisponível.");
      return true;
    }

    if (jedisPool.isClosed()) {
      logAviso("Pool Redis está fechado.");
      return true;
    }

    return false;
  }

  public ConfiguracaoRedis getConfiguracao() {
    return configuracao;
  }

  public JedisPool getJedisPool() {
    return jedisPool;
  }

}