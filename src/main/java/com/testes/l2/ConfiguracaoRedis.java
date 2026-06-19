package com.testes.l2;

public class ConfiguracaoRedis {

  private final String ambiente;
  private final String host;
  private final int port;
  private final String password;
  private final int database;
  private final int timeout;
  private final int maxConnections;
  private final int maxIdleConnections;
  private final int minIdleConnections;
  private final long maxWaitMillis;
  private final boolean blockWhenExhausted;

  private ConfiguracaoRedis(Builder builder) {
    this.ambiente = builder.ambiente;
    this.host = builder.host;
    this.port = builder.port;
    this.password = builder.password;
    this.database = builder.database;
    this.timeout = builder.timeout;
    this.maxConnections = builder.maxConnections;
    this.maxIdleConnections = builder.maxIdleConnections;
    this.minIdleConnections = builder.minIdleConnections;
    this.maxWaitMillis = builder.maxWaitMillis;
    this.blockWhenExhausted = builder.blockWhenExhausted;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getAmbiente() {
    return ambiente;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getPassword() {
    return password;
  }

  public int getDatabase() {
    return database;
  }

  public int getTimeout() {
    return timeout;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public int getMaxIdleConnections() {
    return maxIdleConnections;
  }

  public int getMinIdleConnections() {
    return minIdleConnections;
  }

  public long getMaxWaitMillis() {
    return maxWaitMillis;
  }

  public boolean isBlockWhenExhausted() {
    return blockWhenExhausted;
  }

  public static class Builder {

    private String ambiente;
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int database = 0;
    private int timeout = 2000;

    private int maxConnections = 32;
    private int maxIdleConnections = 16;
    private int minIdleConnections = 4;
    private long maxWaitMillis = 100;
    private boolean blockWhenExhausted = true;

    private Builder() {
    }

    public Builder ambiente(String ambiente) {
      this.ambiente = ambiente;
      return this;
    }

    public Builder host(String host) {
      this.host = host;
      return this;
    }

    public Builder port(int port) {
      this.port = port;
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

    public Builder timeout(int timeout) {
      this.timeout = timeout;
      return this;
    }

    public Builder maxConnections(int maxConnections) {
      this.maxConnections = maxConnections;
      return this;
    }

    public Builder maxIdleConnections(int maxIdleConnections) {
      this.maxIdleConnections = maxIdleConnections;
      return this;
    }

    public Builder minIdleConnections(int minIdleConnections) {
      this.minIdleConnections = minIdleConnections;
      return this;
    }

    public Builder maxWaitMillis(long maxWaitMillis) {
      this.maxWaitMillis = maxWaitMillis;
      return this;
    }

    public Builder blockWhenExhausted(boolean blockWhenExhausted) {
      this.blockWhenExhausted = blockWhenExhausted;
      return this;
    }

    public ConfiguracaoRedis build() {
      validar();
      return new ConfiguracaoRedis(this);
    }

    private void validar() {
      if (host == null || host.trim().isEmpty()) {
        throw new IllegalArgumentException("Host do Redis não pode ser vazio.");
      }

      if (port <= 0 || port > 65535) {
        throw new IllegalArgumentException("Porta do Redis inválida: " + port);
      }

      if (database < 0) {
        throw new IllegalArgumentException("Database do Redis não pode ser negativo.");
      }

      if (timeout <= 0) {
        throw new IllegalArgumentException("Timeout do Redis deve ser maior que zero.");
      }

      if (maxConnections <= 0) {
        throw new IllegalArgumentException("maxConnections deve ser maior que zero.");
      }

      if (maxIdleConnections < 0) {
        throw new IllegalArgumentException("maxIdleConnections não pode ser negativo.");
      }

      if (minIdleConnections < 0) {
        throw new IllegalArgumentException("minIdleConnections não pode ser negativo.");
      }

      if (minIdleConnections > maxIdleConnections) {
        throw new IllegalArgumentException("minIdleConnections não pode ser maior que maxIdleConnections.");
      }

      if (maxIdleConnections > maxConnections) {
        throw new IllegalArgumentException("maxIdleConnections não pode ser maior que maxConnections.");
      }

      if (blockWhenExhausted && maxWaitMillis <= 0) {
        throw new IllegalArgumentException("maxWaitMillis deve ser maior que zero quando blockWhenExhausted for true.");
      }
    }
  }
}
