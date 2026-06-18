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
  private final Duration localCacheTtl;
  private final long localCacheMaxSize;
  private final GenericObjectPoolConfig<Jedis> poolConfig;
  private final boolean circuitBreakerEnabled;
  private final int circuitBreakerFailureThreshold;
  private final Duration circuitBreakerOpenDuration;
  private final Duration failureLogInterval;

  private RedisClientConfig(Builder builder) {
    this.host = builder.host;
    this.port = builder.port;
    this.username = builder.username;
    this.password = builder.password;
    this.database = builder.database;
    this.connectionTimeout = builder.connectionTimeout;
    this.socketTimeout = builder.socketTimeout;
    this.defaultTtl = builder.defaultTtl;
    this.localCacheTtl = builder.localCacheTtl;
    this.localCacheMaxSize = builder.localCacheMaxSize;
    this.poolConfig = builder.poolConfig.clone();
    this.circuitBreakerEnabled = builder.circuitBreakerEnabled;
    this.circuitBreakerFailureThreshold = builder.circuitBreakerFailureThreshold;
    this.circuitBreakerOpenDuration = builder.circuitBreakerOpenDuration;
    this.failureLogInterval = builder.failureLogInterval;
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

  public Duration getLocalCacheTtl() {
    return localCacheTtl;
  }

  public long getLocalCacheMaxSize() {
    return localCacheMaxSize;
  }

  public GenericObjectPoolConfig<Jedis> newPoolConfig() {
    return poolConfig.clone();
  }

  public boolean isCircuitBreakerEnabled() {
    return circuitBreakerEnabled;
  }

  public int getCircuitBreakerFailureThreshold() {
    return circuitBreakerFailureThreshold;
  }

  public Duration getCircuitBreakerOpenDuration() {
    return circuitBreakerOpenDuration;
  }

  public Duration getFailureLogInterval() {
    return failureLogInterval;
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
    private Duration localCacheTtl = Duration.ofSeconds(5);
    private long localCacheMaxSize = 10_000;
    private GenericObjectPoolConfig<Jedis> poolConfig = defaultPoolConfig();
    private boolean circuitBreakerEnabled = true;
    private int circuitBreakerFailureThreshold = 3;
    private Duration circuitBreakerOpenDuration = Duration.ofSeconds(15);
    private Duration failureLogInterval = Duration.ofSeconds(30);

    private static GenericObjectPoolConfig<Jedis> defaultPoolConfig() {
      GenericObjectPoolConfig<Jedis> config = new GenericObjectPoolConfig<>();
      config.setMaxTotal(64);
      config.setMaxIdle(16);
      config.setMinIdle(4);
      config.setBlockWhenExhausted(false);
      config.setTestOnBorrow(false);
      config.setTestWhileIdle(true);
      config.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
      config.setMinEvictableIdleDuration(Duration.ofMinutes(1));
      config.setJmxEnabled(false);
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

    public Builder localCacheTtl(Duration localCacheTtl) {
      this.localCacheTtl = Objects.requireNonNull(localCacheTtl, "localCacheTtl");
      return this;
    }

    public Builder localCacheMaxSize(long localCacheMaxSize) {
      this.localCacheMaxSize = localCacheMaxSize;
      return this;
    }

    public Builder poolConfig(GenericObjectPoolConfig<Jedis> poolConfig) {
      this.poolConfig = Objects.requireNonNull(poolConfig, "poolConfig").clone();
      return this;
    }

    public Builder poolMaxTotal(int maxTotal) {
      poolConfig.setMaxTotal(maxTotal);
      return this;
    }

    public Builder poolMaxIdle(int maxIdle) {
      poolConfig.setMaxIdle(maxIdle);
      return this;
    }

    public Builder poolMinIdle(int minIdle) {
      poolConfig.setMinIdle(minIdle);
      return this;
    }

    public Builder poolBlockWhenExhausted(boolean blockWhenExhausted) {
      poolConfig.setBlockWhenExhausted(blockWhenExhausted);
      return this;
    }

    public Builder poolTestWhileIdle(boolean testWhileIdle) {
      poolConfig.setTestWhileIdle(testWhileIdle);
      return this;
    }

    public Builder poolEvictionInterval(Duration interval) {
      poolConfig.setTimeBetweenEvictionRuns(Objects.requireNonNull(interval, "interval"));
      return this;
    }

    public Builder poolMinEvictableIdle(Duration duration) {
      poolConfig.setMinEvictableIdleDuration(Objects.requireNonNull(duration, "duration"));
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

    public Builder circuitBreakerOpenDuration(Duration duration) {
      this.circuitBreakerOpenDuration = Objects.requireNonNull(duration, "duration");
      return this;
    }

    public Builder failureLogInterval(Duration failureLogInterval) {
      this.failureLogInterval = Objects.requireNonNull(failureLogInterval, "failureLogInterval");
      return this;
    }

    public RedisClientConfig build() {
      if (host.isBlank()) {
        throw new IllegalArgumentException("host must not be blank");
      }
      if (port <= 0 || port > 65_535) {
        throw new IllegalArgumentException("port must be between 1 and 65535");
      }
      if (database < 0) {
        throw new IllegalArgumentException("database must be >= 0");
      }
      if (connectionTimeout.isNegative() || connectionTimeout.isZero()) {
        throw new IllegalArgumentException("connectionTimeout must be > 0");
      }
      if (socketTimeout.isNegative() || socketTimeout.isZero()) {
        throw new IllegalArgumentException("socketTimeout must be > 0");
      }
      if (defaultTtl.isNegative() || defaultTtl.isZero()) {
        throw new IllegalArgumentException("defaultTtl must be > 0");
      }
      if (localCacheTtl.isNegative() || localCacheTtl.isZero()) {
        throw new IllegalArgumentException("localCacheTtl must be > 0");
      }
      if (localCacheMaxSize <= 0) {
        throw new IllegalArgumentException("localCacheMaxSize must be > 0");
      }
      if (circuitBreakerFailureThreshold <= 0) {
        throw new IllegalArgumentException("circuitBreakerFailureThreshold must be > 0");
      }
      if (circuitBreakerOpenDuration.isNegative() || circuitBreakerOpenDuration.isZero()) {
        throw new IllegalArgumentException("circuitBreakerOpenDuration must be > 0");
      }
      if (failureLogInterval.isNegative() || failureLogInterval.isZero()) {
        throw new IllegalArgumentException("failureLogInterval must be > 0");
      }
      return new RedisClientConfig(this);
    }
  }
}
