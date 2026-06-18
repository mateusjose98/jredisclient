# Redis Cache Client - Java 11

Biblioteca de cache para Redis com foco em resiliencia para aplicacoes JEE e webapps multithread.

## Dependencias do projeto

- `redis.clients:jedis:5.1.3`
- `com.github.ben-manes.caffeine:caffeine:3.1.8`
- `org.slf4j:slf4j-api:2.0.13`

Para testes:

- `org.junit.jupiter:junit-jupiter:5.10.2`
- o projeto de exemplo nao empacota binding SLF4J; a aplicacao cliente deve fornecer o binding desejado

## O que a lib entrega

- `JedisPool` com configuracao feita pelo app cliente.
- Cache local L1 por instancia com TTL curto e `maximumSize`.
- Fallback silencioso para o cache local quando o Redis cair.
- `CircuitBreaker` para fast-fail e para evitar cascata de timeout.
- Nenhuma thread interna da lib, o que evita atrito com containers JEE.
- Suporte a objetos com serializacao pluggable.

## Contrato de falha

Quando o Redis estiver indisponivel:

- `get(...)` retorna `Optional.empty()` se nao houver valor no L1.
- `getOrDefault(...)` retorna o valor default se nao houver valor no L1.
- `getObject(...)` retorna `Optional.empty()` se nao houver valor no L1.
- `getObjectOrDefault(...)` retorna o valor default se nao houver valor no L1.
- `set(...)` retorna `false`, mas mantem o valor no cache local da instancia.
- `setObject(...)` retorna `false`, mas mantem o valor no cache local da instancia.
- `delete(...)` retorna `false`.
- `increment/incrementBy(...)` retornam `0`.

Ou seja: a indisponibilidade do Redis nao sobe excecao para o codigo de negocio.

## API principal

Classes publicas:

- `com.testes.redis.RedisClientConfig`
- `com.testes.redis.RedisClients`
- `com.testes.redis.RedisCacheClient`
- `com.testes.redis.JedisRedisCacheClientImpl`
- `com.testes.redis.RedisClientStatus`
- `com.testes.redis.RedisSerializer`
- `com.testes.redis.RedisSerializers`

## Suporte a objetos

A lib agora suporta dois modos:

- Objeto `Serializable` com serializer padrao da propria lib.
- Objeto qualquer usando `RedisSerializer<T>` custom.
- Conversao JSON nativa com suporte a datas via `RedisSerializers.json(...)`.

Datas suportadas no serializer JSON nativo:

- `java.time.LocalDate`
- `java.time.LocalDateTime`
- `java.time.LocalTime`
- `java.time.Instant`
- `java.time.OffsetDateTime`
- `java.time.ZonedDateTime`
- `java.util.Date`

Exemplo com `Serializable`:

```java
public class ProdutoCache implements java.io.Serializable {
  private final String id;
  private final int versao;

  public ProdutoCache(String id, int versao) {
    this.id = id;
    this.versao = versao;
  }
}

ProdutoCache value = new ProdutoCache("123", 7);
client.setObject("produto:123", value, Duration.ofMinutes(2));

ProdutoCache cached = client.getObjectOrDefault(
    "produto:123",
    new ProdutoCache("default", 0),
    ProdutoCache.class);
```

Exemplo com serializer custom:

```java
public final class ProdutoSerializer implements RedisSerializer<Produto> {
  @Override
  public Class<Produto> targetType() {
    return Produto.class;
  }

  @Override
  public String serialize(Produto value) {
    return value.getId() + "|" + value.getNome();
  }

  @Override
  public Produto deserialize(String payload) {
    String[] parts = payload.split("\\|", 2);
    return new Produto(parts[0], parts[1]);
  }
}

RedisSerializer<Produto> serializer = new ProdutoSerializer();
client.setObject("produto:custom:1", new Produto("1", "Mouse"), Duration.ofMinutes(2), serializer);
Produto cached = client.getObjectOrDefault("produto:custom:1", null, serializer);
```

Exemplo com JSON e datas:

```java
public class PedidoCache {
  private String id;
  private java.time.LocalDate businessDate;
  private java.time.LocalDateTime generatedAt;
  private java.util.Date legacyDate;

  public PedidoCache() {
  }

  public PedidoCache(String id, LocalDate businessDate, LocalDateTime generatedAt, Date legacyDate) {
    this.id = id;
    this.businessDate = businessDate;
    this.generatedAt = generatedAt;
    this.legacyDate = legacyDate;
  }

  // getters/setters
}

RedisSerializer<PedidoCache> jsonSerializer = RedisSerializers.json(PedidoCache.class);

PedidoCache payload = new PedidoCache(
    "pedido-1",
    LocalDate.now(),
    LocalDateTime.now(),
    new Date());

client.setObject("pedido:1", payload, Duration.ofMinutes(2), jsonSerializer);
PedidoCache cached = client.getObjectOrDefault("pedido:1", null, jsonSerializer);
```

Observacao:

- Para conversao JSON, prefira objetos com construtor sem argumentos e getters/setters publicos, ou anote a classe para Jackson conforme o seu padrao.

## Exemplo de configuracao

```java
import com.testes.redis.RedisCacheClient;
import com.testes.redis.RedisClientConfig;
import com.testes.redis.RedisClients;

import java.time.Duration;

RedisClientConfig config = RedisClientConfig.builder()
    .host("redis.interno")
    .port(6379)
    .database(0)
    .connectionTimeout(Duration.ofMillis(800))
    .socketTimeout(Duration.ofMillis(800))
    .defaultTtl(Duration.ofMinutes(5))
    .localCacheTtl(Duration.ofSeconds(5))
    .localCacheMaxSize(10_000)
    .poolMaxTotal(128)
    .poolMaxIdle(32)
    .poolMinIdle(8)
    .poolBlockWhenExhausted(false)
    .circuitBreakerFailureThreshold(3)
    .circuitBreakerOpenDuration(Duration.ofSeconds(15))
    .build();

RedisCacheClient client = RedisClients.create(config);
```

## Exemplo de uso

```java
String key = "produto:123";

String value = client.getOrDefault(key, null);
if (value == null) {
  value = "resultado-processado";
  client.set(key, value, Duration.ofMinutes(2));
}
```

## Uso em JEE

```java
package br.com.seuapp.infra;

import com.testes.redis.RedisCacheClient;
import com.testes.redis.RedisClientConfig;
import com.testes.redis.RedisClients;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import java.time.Duration;

@ApplicationScoped
public class RedisProducer {

  private RedisCacheClient client;

  @Produces
  @ApplicationScoped
  public RedisCacheClient redisCacheClient() {
    if (client == null) {
      client = RedisClients.create(
          RedisClientConfig.builder()
              .host(System.getProperty("app.redis.host", "localhost"))
              .port(Integer.parseInt(System.getProperty("app.redis.port", "6379")))
              .defaultTtl(Duration.ofMinutes(5))
              .localCacheTtl(Duration.ofSeconds(5))
              .localCacheMaxSize(10_000)
              .poolMaxTotal(128)
              .build());
    }
    return client;
  }

  @PreDestroy
  public void shutdown() {
    if (client != null) {
      client.close();
    }
  }
}
```

## Observacoes de desenho

- O L1 e local a cada JVM/instancia. Em ambiente com varias VPSs ele nao e compartilhado.
- O L1 usa TTL curto para reduzir staleness entre instancias.
- `maximumSize` limita memoria para evitar crescimento indefinido.
- O `CircuitBreaker` evita que a aplicacao fique pagando timeout de rede em cada requisicao durante quedas do Redis.
- O pool usa `blockWhenExhausted(false)` por default para falhar rapido em vez de prender threads da aplicacao.

## Build

Se o Maven nao estiver no `PATH`, rode o `mvn.cmd` do IntelliJ:

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.3.4\plugins\maven\lib\maven3\bin\mvn.cmd" test
```

## Main de teste manual

Classe pronta:

- `com.testes.Main`

Smoke test basico:

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.3.4\plugins\maven\lib\maven3\bin\mvn.cmd" -q package dependency:copy-dependencies
java -cp target\classes;target\dependency\* -Dredis.host=localhost -Dredis.port=6379 com.testes.Main
```

Teste de fallback L1:

```powershell
java -cp target\classes;target\dependency\* -Dredis.host=localhost -Dredis.port=6379 -Dtest.mode=fallback -Dredis.local.ttl.ms=10000 -Dtest.pause.before.fallback.ms=5000 com.testes.Main
```

Propriedades uteis:

- `redis.host`, `redis.port`, `redis.username`, `redis.password`, `redis.database`
- `redis.connect.timeout.ms`, `redis.socket.timeout.ms`
- `redis.local.ttl.ms`, `redis.local.max.size`
- `redis.pool.max.total`, `redis.pool.max.idle`, `redis.pool.min.idle`
- `redis.cb.enabled`, `redis.cb.failure.threshold`, `redis.cb.open.seconds`
- `test.key`, `test.object.key`, `test.counter.key`, `test.value`, `test.ttl.seconds`, `test.mode`
- `test.local.printer.enabled`, `test.local.printer.interval.ms`, `test.keep.alive.ms`
