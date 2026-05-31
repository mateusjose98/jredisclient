package com.testes.redis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.Objects;

public final class RedisClientConfig {

  private final String host;
  private final int port;
  private final String username;
  private final String password;
  private final int database;
  private final Duration connectionTimeout;
  private final Duration socketTimeout;
  private final Duration defaultTtl;
  private final GenericObjectPoolConfig<Jedis> poolConfig;
  private final boolean circuitBreakerEnabled;
  private final int circuitBreakerFailureThreshold;
  private final long circuitBreakerResetTimeoutMs;
  private final boolean healthCheckEnabled;
  private final Duration healthCheckInterval;

  private RedisClientConfig(Builder builder) {
    this.host = builder.host;
    this.port = builder.port;
    this.username = builder.username;
    this.password = builder.password;
    this.database = builder.database;
    this.connectionTimeout = builder.connectionTimeout;
    this.socketTimeout = builder.socketTimeout;
    this.defaultTtl = builder.defaultTtl;
    this.poolConfig = builder.poolConfig;
    this.circuitBreakerEnabled = builder.circuitBreakerEnabled;
    this.circuitBreakerFailureThreshold = builder.circuitBreakerFailureThreshold;
    this.circuitBreakerResetTimeoutMs = builder.circuitBreakerResetTimeoutMs;
    this.healthCheckEnabled = builder.healthCheckEnabled;
    this.healthCheckInterval = builder.healthCheckInterval;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public int getDatabase() {
    return database;
  }

  public Duration getConnectionTimeout() {
    return connectionTimeout;
  }

  public Duration getSocketTimeout() {
    return socketTimeout;
  }

  public Duration getDefaultTtl() {
    return defaultTtl;
  }

  public GenericObjectPoolConfig<Jedis> getPoolConfig() {
    return poolConfig;
  }

  public boolean isCircuitBreakerEnabled() {
    return circuitBreakerEnabled;
  }

  public int getCircuitBreakerFailureThreshold() {
    return circuitBreakerFailureThreshold;
  }

  public long getCircuitBreakerResetTimeoutMs() {
    return circuitBreakerResetTimeoutMs;
  }

  public boolean isHealthCheckEnabled() {
    return healthCheckEnabled;
  }

  public Duration getHealthCheckInterval() {
    return healthCheckInterval;
  }

  public static final class Builder {
    private String host = "localhost";
    private int port = 6379;
    private String username;
    private String password;
    private int database;
    private Duration connectionTimeout = Duration.ofMillis(1_500);
    private Duration socketTimeout = Duration.ofMillis(1_500);
    private Duration defaultTtl = Duration.ofMinutes(5);
    private GenericObjectPoolConfig<Jedis> poolConfig = defaultPoolConfig();
    private boolean circuitBreakerEnabled = true;
    private int circuitBreakerFailureThreshold = 5;
    private long circuitBreakerResetTimeoutMs = 30_000;
    private boolean healthCheckEnabled = true;
    private Duration healthCheckInterval = Duration.ofSeconds(30);

    private static GenericObjectPoolConfig<Jedis> defaultPoolConfig() {
      GenericObjectPoolConfig<Jedis> config = new GenericObjectPoolConfig<>();
      config.setMaxTotal(100);
      config.setMaxIdle(32);
      config.setMinIdle(16);
      config.setBlockWhenExhausted(false);
      config.setTestOnBorrow(false);
      config.setTestWhileIdle(true);
      config.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
      config.setMinEvictableIdleDuration(Duration.ofMinutes(1));
      return config;
    }

    public Builder host(String host) {
      this.host = Objects.requireNonNull(host, "host");
      return this;
    }

    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public Builder username(String username) {
      this.username = username;
      return this;
    }

    public Builder password(String password) {
      this.password = password;
      return this;
    }

    public Builder database(int database) {
      this.database = database;
      return this;
    }

    public Builder connectionTimeout(Duration connectionTimeout) {
      this.connectionTimeout = Objects.requireNonNull(connectionTimeout, "connectionTimeout");
      return this;
    }

    public Builder socketTimeout(Duration socketTimeout) {
      this.socketTimeout = Objects.requireNonNull(socketTimeout, "socketTimeout");
      return this;
    }

    public Builder defaultTtl(Duration defaultTtl) {
      this.defaultTtl = Objects.requireNonNull(defaultTtl, "defaultTtl");
      return this;
    }

    public Builder poolConfig(GenericObjectPoolConfig<Jedis> poolConfig) {
      this.poolConfig = Objects.requireNonNull(poolConfig, "poolConfig");
      return this;
    }

    public Builder circuitBreakerEnabled(boolean enabled) {
      this.circuitBreakerEnabled = enabled;
      return this;
    }

    public Builder circuitBreakerFailureThreshold(int threshold) {
      this.circuitBreakerFailureThreshold = threshold;
      return this;
    }

    public Builder circuitBreakerResetTimeoutMs(long timeoutMs) {
      this.circuitBreakerResetTimeoutMs = timeoutMs;
      return this;
    }

    public Builder healthCheckEnabled(boolean enabled) {
      this.healthCheckEnabled = enabled;
      return this;
    }

    public Builder healthCheckInterval(Duration interval) {
      this.healthCheckInterval = Objects.requireNonNull(interval, "healthCheckInterval");
      return this;
    }

    public RedisClientConfig build() {
      if (port <= 0 || port > 65535) {
        throw new IllegalArgumentException("port must be between 1 and 65535");
      }
      if (database < 0) {
        throw new IllegalArgumentException("database must be >= 0");
      }
      return new RedisClientConfig(this);
    }
  }
}