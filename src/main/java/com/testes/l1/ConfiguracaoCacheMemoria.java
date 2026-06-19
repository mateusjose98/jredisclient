package com.testes.l1;

public class ConfiguracaoCacheMemoria {

  private final long ttlSegundos;
  private final long tamanhoMaximo;

  public ConfiguracaoCacheMemoria(long ttlSegundos, long tamanhoMaximo) {
    this.ttlSegundos = ttlSegundos;
    this.tamanhoMaximo = tamanhoMaximo;
  }

  public long getTtlSegundos() {
    return ttlSegundos;
  }

  public long getTamanhoMaximo() {
    return tamanhoMaximo;
  }
}
