# Redis Cache Client (JedisPool) - Java 11

Biblioteca para cache Redis (single node) com foco em resiliencia:

- Nao derruba a aplicacao cliente se o Redis ficar indisponivel.
- Loga cada operacao com tempo de execucao.
- API simples para integracao rapida.
- Funciona bem com cliente em ambiente cluster (multiplas instancias da aplicacao).

## 1. O que esta implementado

Principais classes:

- `com.testes.redis.RedisClientConfig`
- `com.testes.redis.RedisClients`
- `com.testes.redis.RedisCacheClient`
- `com.testes.redis.JedisRedisCacheClientImpl`
- `com.testes.redis.CircuitBreaker` (padrao para falhas frequentes)
- `com.testes.redis.RedisHealthChecker` (monitoramento assincrone)

Comportamento de resiliencia:

- Em falha de conexao/timeout/comando Redis, a biblioteca registra `WARN` e retorna fallback.
- A excecao nao e propagada para o codigo de negocio.
- **Circuit Breaker habilitado por padrao**: Detecta Redis indisponivel e faz fast-fail (reduz latencia).
- **Health Check assincrone**: Verifica conectividade a cada 30s em thread background.

### 1.1 Circuit Breaker (producao-ready)

Quando o circuit breaker detecta falhas consecutivas (default: 5 falhas), muda para estado OPEN:

- Novas requisicoes retornam fallback imediatamente (< 1ms), sem tentar conexao.
- Apos timeout (default: 30s), tenta reconectar em HALF_OPEN.
- Se reconexao falhar, volta para OPEN; se tiver sucesso, volta para CLOSED.

**Beneficio de performance em cenario de falha:**

- Sem circuit breaker: 100 req/s × 1.5s timeout = 150s de latencia acumulada
- Com circuit breaker: 100 req/s × <1ms = <0.1s de latencia total

Configuracao opcional (valores padrao sao bons):

```java
config.circuitBreakerFailureThreshold(5)    // falhas antes de OPEN
      .circuitBreakerResetTimeoutMs(30_000) // tempo de reset
```

### 1.2 Health Check (background, sem overhead)

Roda em daemon thread separada, pinga Redis periodicamente (default: 30s):

- Nao bloqueia requisicoes da aplicacao.
- Overhead: ~1 conexao PING a cada 30 segundos.
- Util para alertar quando Redis fica indisponivel.

Configuracao opcional:

```java
config.healthCheckInterval(Duration.ofSeconds(30))
```

## 2. Dependencia no app cliente

Publique este projeto no seu repositorio (Nexus/Artifactory) ou no Maven local:

```bash
mvn clean install
```

Depois, no projeto cliente:

```xml
<dependency>
  <groupId>com.testes</groupId>
  <artifactId>redisclient</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

## 3. Uso em Spring Boot

### 3.1 Configuracao de propriedades

`application.yml`:

```yaml
app:
  redis:
    host: localhost
    port: 6379
    username: ''
    password: ''
    database: 0
    connect-timeout-ms: 1500
    socket-timeout-ms: 1500
    default-ttl-seconds: 300
```

### 3.2 Bean de configuracao

```java
package com.seuapp.config;

import com.testes.redis.RedisCacheClient;
import com.testes.redis.RedisClientConfig;
import com.testes.redis.RedisClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedisCacheConfiguration {

  @Bean(destroyMethod = "close")
  public RedisCacheClient redisCacheClient(
      @Value("${app.redis.host:localhost}") String host,
      @Value("${app.redis.port:6379}") int port,
      @Value("${app.redis.username:}") String username,
      @Value("${app.redis.password:}") String password,
      @Value("${app.redis.database:0}") int database,
      @Value("${app.redis.connect-timeout-ms:1500}") long connectTimeoutMs,
      @Value("${app.redis.socket-timeout-ms:1500}") long socketTimeoutMs,
      @Value("${app.redis.default-ttl-seconds:300}") long ttlSeconds,
      @Value("${app.redis.circuit-breaker-enabled:true}") boolean circuitBreakerEnabled,
      @Value("${app.redis.health-check-enabled:true}") boolean healthCheckEnabled
  ) {
    RedisClientConfig config = RedisClientConfig.builder()
        .host(host)
        .port(port)
        .username(emptyToNull(username))
        .password(emptyToNull(password))
        .database(database)
        .connectionTimeout(Duration.ofMillis(connectTimeoutMs))
        .socketTimeout(Duration.ofMillis(socketTimeoutMs))
        .defaultTtl(Duration.ofSeconds(ttlSeconds))
        .circuitBreakerEnabled(circuitBreakerEnabled)
        .healthCheckEnabled(healthCheckEnabled)
        .build();

    return RedisClients.create(config);
  }

  private String emptyToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }
}
```

### 3.3 Propriedades no application.yml

```yaml
app:
  redis:
    host: localhost
    port: 6379
    username: ''
    password: ''
    database: 0
    connect-timeout-ms: 1500
    socket-timeout-ms: 1500
    default-ttl-seconds: 300
    circuit-breaker-enabled: true # opcional, true por padrao
    health-check-enabled: true # opcional, true por padrao
```

### 3.4 Uso em service

```java
package com.seuapp.service;

import com.testes.redis.RedisCacheClient;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ProdutoCacheService {

  private final RedisCacheClient redis;

  public ProdutoCacheService(RedisCacheClient redis) {
    this.redis = redis;
  }

  public String buscarOuCachear(String key, String valorCalculado) {
    String cached = redis.getOrDefault(key, null);
    if (cached != null) {
      return cached;
    }

    redis.set(key, valorCalculado, Duration.ofMinutes(2));
    return valorCalculado;
  }
}
```

### 3.5 Observabilidade no Spring Boot

Exemplo para ver logs da biblioteca:

```yaml
logging:
  level:
    com.testes.redis: INFO
```

Em indisponibilidade do Redis:

- Logs com `status=failure` ou `status=circuit-open` quando circuit breaker está ativo.
- Saude: `redis.health-check` registra `status=healthy` ou `status=unhealthy` a cada ciclo.
- A aplicacao continua funcionando normalmente.

Exemplo para expor status do Redis via Spring:

```java
@RestController
@RequestMapping("/actuator/redis")
public class RedisStatusEndpoint {

  @Autowired
  private SafeJedisPoolRedisCacheClient redis;

  @GetMapping("/status")
  public Map<String, String> status() {
    return Map.of("status", redis.getStatus());
  }
}
```

## 4. Uso em app legado JEE8 no WebLogic

## 4.1 Dependencia Maven

No projeto WAR/EAR, adicione:

```xml
<dependency>
  <groupId>com.testes</groupId>
  <artifactId>redisclient</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

## 4.2 Producao de recurso via CDI

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
public class RedisCacheProducer {

  private RedisCacheClient client;

  @Produces
  @ApplicationScoped
  public RedisCacheClient redisCacheClient() {
    if (client == null) {
      RedisClientConfig config = RedisClientConfig.builder()
          .host(getEnv("APP_REDIS_HOST", "localhost"))
          .port(Integer.parseInt(getEnv("APP_REDIS_PORT", "6379")))
          .username(emptyToNull(getEnv("APP_REDIS_USERNAME", "")))
          .password(emptyToNull(getEnv("APP_REDIS_PASSWORD", "")))
          .database(Integer.parseInt(getEnv("APP_REDIS_DB", "0")))
          .connectionTimeout(Duration.ofMillis(Long.parseLong(getEnv("APP_REDIS_CONNECT_TIMEOUT_MS", "1500"))))
          .socketTimeout(Duration.ofMillis(Long.parseLong(getEnv("APP_REDIS_SOCKET_TIMEOUT_MS", "1500"))))
          .defaultTtl(Duration.ofSeconds(Long.parseLong(getEnv("APP_REDIS_DEFAULT_TTL_SECONDS", "300"))))
          .circuitBreakerEnabled(Boolean.parseBoolean(getEnv("APP_REDIS_CIRCUIT_BREAKER_ENABLED", "true")))
          .healthCheckEnabled(Boolean.parseBoolean(getEnv("APP_REDIS_HEALTH_CHECK_ENABLED", "true")))
          .healthCheckInterval(Duration.ofSeconds(Long.parseLong(getEnv("APP_REDIS_HEALTH_CHECK_INTERVAL_SECONDS", "30"))))
          .build();

      client = RedisClients.create(config);
    }
    return client;
  }

  @PreDestroy
  public void shutdown() {
    if (client != null) {
      client.close();
    }
  }

  private String getEnv(String key, String defaultValue) {
    String value = System.getProperty(key);
    if (value == null || value.isBlank()) {
      value = System.getenv(key);
    }
    return (value == null || value.isBlank()) ? defaultValue : value;
  }

  private String emptyToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }
}
```

## 4.3 Uso em bean legado

```java
package br.com.seuapp.core;

import com.testes.redis.RedisCacheClient;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import java.time.Duration;

@RequestScoped
public class ConsultaService {

  @Inject
  private RedisCacheClient redis;

  public String consultar(String chave) {
    String valor = redis.getOrDefault(chave, null);
    if (valor != null) {
      return valor;
    }

    String calculado = "resultado";
    redis.set(chave, calculado, Duration.ofMinutes(5));
    return calculado;
  }
}
```

## 4.4 Notas para WebLogic

- Garanta fechamento do cliente no ciclo de vida da aplicacao (`@PreDestroy`).
- Prefira configurar host/porta/credenciais por variavel de ambiente ou `-D` no startup do servidor.
- Se existir conflito de logging no dominio, mantenha um unico binding SLF4J ativo na aplicacao.

## 5. Contrato de falha (importante)

Quando o Redis estiver indisponivel:

- `get(...)` retorna `Optional.empty()`.
- `getOrDefault(...)` retorna o default.
- `set(...)` retorna `false`.
- `delete(...)` retorna `false`.
- `increment/incrementBy` retornam `0` em fallback.

Ou seja, a aplicacao segue executando normalmente, apenas com perda temporaria de cache.

## 6. Teste de carga

Classe pronta no projeto:

- `com.testes.redis.RedisLoadTestMain`

Parametros por `-D`:

- `redis.host` (default: `localhost`)
- `redis.port` (default: `6379`)
- `load.threads` (default: `16`)
- `load.iterations` (default: `5000`)
- `load.ttl.seconds` (default: `60`)

Exemplo:

```bash
java -Dredis.host=localhost -Dredis.port=6379 -Dload.threads=32 -Dload.iterations=10000 -cp "..." com.testes.redis.RedisLoadTestMain
```

## 6.1 Teste de Circuit Breaker na pratica

Classe pronta para demonstrar circuit breaker em acao:

- `com.testes.redis.CircuitBreakerTestMain`

Executa 4 fases:

1. **Phase 1 (5s)**: Operacoes normais com Redis funcionando (CLOSED)
2. **Phase 2 (10s)**: Simula falha usando host invalido, circuit breaker abre (OPEN), fallbacks rapidos (<1ms)
3. **Phase 3 (15s)**: Aguarda reset timeout (default 10s), circuit breaker tenta reconectar (HALF_OPEN)
4. **Phase 4 (5s)**: Volta ao host valido, reconexao bem-sucedida (CLOSED)

Parametros por `-D`:

- `redis.host` (default: `localhost`)
- `redis.port` (default: `6379`)
- `test.failure` (default: `true` - simula falha, use `false` para skip Phase 2)

Exemplo completo (mostra ciclo full):

```bash
java -Dredis.host=localhost -Dredis.port=6379 -Dtest.failure=true -cp "..." com.testes.redis.CircuitBreakerTestMain
```

Saida esperada (com Redis ligado):

```
=== Redis Circuit Breaker Test ===
redis.host=localhost
redis.port=6379
test.failure=true

Phase 1: Normal operations (5s)
[OK] op=1 set=true get=value-1234567890
[OK] status=circuitBreaker=CLOSED health=unchecked
[OK] op=2 set=true get=value-1234567891
...

Phase 2: Forcing failure with invalid host (10s)
[FAIL] op=1 set=false get=FALLBACK
[FAIL] op=2 set=false get=FALLBACK
[FAIL] op=3 set=false get=FALLBACK
[FAIL] status=circuitBreaker=OPEN health=unchecked
[FAIL] op=4 set=false get=FALLBACK
[FAIL] op=5 set=false get=FALLBACK
(note: requests after circuit opens return FALLBACK in <1ms, nao fazem timeout)

Phase 3: Waiting for circuit breaker reset (15s)
(aguardando reset timeout)

Phase 4: Attempting reconnection (5s)
[RECONNECT] op=1 set=true get=value-1234567892
[RECONNECT] status=circuitBreaker=HALF_OPEN -> CLOSED
[RECONNECT] op=2 set=true get=value-1234567893
...

=== Test Complete ===
Final status: circuitBreaker=CLOSED health=unchecked
```

### Observar em tempo real

Abra outro terminal e monitore logs:

```bash
tail -f arquivo-logs.log | grep "redis\."
```

Voce vera:

- `redis.operation=SET ... status=success` durante Phase 1
- `redis.operation=SET ... status=failure` durante Phase 2 (com timeouts baixos)
- `redis.operation=SET ... status=circuit-open` (quando circuit esta OPEN, fallback rapido)
- Transicao para `status=success` novamente em Phase 4

## 7. Boas praticas em ambiente cluster (app)

- Use chave de cache deterministicamente (prefixos por dominio de negocio).
- Defina TTL curto/medio para reduzir staleness.
- Trate cache como acelerador, nunca como fonte de verdade.
- Monitore taxa de falha via logs (`status=failure` ou `status=circuit-open`).

## 8. Production-Ready: Circuit Breaker e Performance

### Por que circuit breaker nao deixa lento?

Cenario: Redis cai, app com 100 req/s tentando usar cache.

**Sem circuit breaker:**

```
req1 -> timeout 1.5s -> fallback
req2 -> timeout 1.5s -> fallback
req3 -> timeout 1.5s -> fallback
...
100 req/s × 1.5s = 150 segundos de latencia acumulada
App fica lenta, usuarios reclamam
```

**Com circuit breaker:**

```
req1-5 -> timeout 1.5s (5 falhas)
req6+ -> circuit OPEN -> <1ms -> fallback (fast fail)
100 req/s × <1ms = <100ms latencia acumulada
App permanece responsiva
```

**Overhead do circuit breaker em path de sucesso:**

- Apenas uma comparacao de estado atomico (< 1 microsegundo)
- Nem consegue medir, perdido no ruido de latencia de rede

### Desabilitar se necessario

Se por algum motivo quiser circuit breaker OFF:

```yaml
# application.yml
app.redis.circuit-breaker-enabled: false
```

```java
// WebLogic
config.circuitBreakerEnabled(false)
```

Mesma coisa para health check:

```yaml
app.redis.health-check-enabled: false
```

## 9. Escalabilidade: Pool config para alta carga

Default pool (100 maxTotal) funciona bem para ~1k req/s. Para mais:

```yaml
# application.yml para 10k req/s com multiplas instancias
app.redis.pool-max-total: 256 # aumenta limite
app.redis.pool-max-idle: 64
app.redis.pool-min-idle: 32
```

```java
// Builder
config.poolConfig(customPoolConfig);
```

Regra pratica:

- Default (100): OK ate 1k req/s
- 256: OK ate 10k req/s
- 512+: Para casos extremos com muita concorrencia

Health check vai acompanhar: a cada ciclo (30s) faz 1 PING, totalmente negligenciavel.
