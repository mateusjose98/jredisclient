package com.testes;

import java.util.Optional;

import com.testes.l2.ConfiguracaoRedis;
import com.testes.l2.RedisCacheApi;

public class MainL2 {

  public static void main(String[] args) {
    ConfiguracaoRedis configuracaoRedis = ConfiguracaoRedis.builder()
        .host("localhost")
        .port(6379)
        .database(0)
        .timeout(2000)
        .maxConnections(32)
        .maxIdleConnections(16)
        .minIdleConnections(4)
        .maxWaitMillis(100)
        .blockWhenExhausted(true)
        .build();
    RedisCacheApi cacheRedis = new RedisCacheApi(configuracaoRedis);

    cacheRedis.put("chave1", "valor11111");

    Optional<String> valor = cacheRedis.get("chave1", String.class);
    System.out.println("Valor obtido: " + valor.orElse("null"));

    cacheRedis.close();

  }

}
