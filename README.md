# redisclient

Biblioteca Java para cache em dois niveis:

- L1 em memoria com Caffeine (baixa latencia).
- L2 em Redis com Jedis (compartilhado entre instancias da aplicacao).

O projeto oferece:

- API de cache em memoria (`InMemoryCacheApi`).
- API de cache Redis com serializacao JSON (`RedisCacheApi`).
- Gerenciador L1/L2 com fallback automatico (`L1L2CacheManager`).

## Objetivo do projeto

Este projeto foi feito para simplificar um padrao comum em sistemas de alta leitura:

1. Buscar primeiro no cache local (L1).
2. Se nao encontrar, buscar no Redis (L2).
3. Se encontrar no L2, repopular o L1.

Com isso, voce reduz latencia media e diminui carga no Redis para chaves muito acessadas.

## Stack e requisitos

- Java 11
- Maven 3.8+
- Redis 6+ (ou compativel)

Dependencias principais:

- Caffeine
- Jedis
- Jackson (serializacao/desserializacao)

## Estrutura principal

- `src/main/java/com/testes/l1/ConfiguracaoCacheMemoria.java`
- `src/main/java/com/testes/l1/InMemoryCacheApi.java`
- `src/main/java/com/testes/l2/ConfiguracaoRedis.java`
- `src/main/java/com/testes/l2/JacksonCacheSerializer.java`
- `src/main/java/com/testes/l2/RedisCacheApi.java`
- `src/main/java/com/testes/L1L2CacheManager.java`
- `src/main/java/com/testes/MainL1.java`
- `src/main/java/com/testes/MainL2.java`
- `src/main/java/com/testes/MainL1L2.java`

## Como funciona cada camada

### L1 - InMemoryCacheApi

Implementado com Caffeine, com dois controles:

- `ttlSegundos`: tempo de expiracao apos escrita.
- `tamanhoMaximo`: quantidade maxima de entradas.

Operacoes:

- `put(key, value)`
- `get(key, Class<T>)`
- `remove(key)`

### L2 - RedisCacheApi

Implementado com pool de conexoes Jedis e serializacao em JSON.

Operacoes principais:

- `put(key, value)`
- `put(key, value, ttlSeconds)`
- `get(key, Class<T>)`
- `exists(key)`
- `ttl(key)`
- `expire(key, ttlSeconds)`
- `remove(key)`

Observacoes importantes:

- Quando `ambiente` e informado, a chave no Redis vira `ambiente:chave`.
- A API ignora entradas invalidas (por exemplo, `value == null` ou `ttl <= 0`) e registra aviso em log.

### L1 + L2 - L1L2CacheManager

Fluxo de leitura:

1. Tenta L1.
2. Se nao achar, tenta L2.
3. Se achar no L2, grava no L1.

Fluxo de escrita:

- Grava no L1 e no L2 (com TTL no L2).

## Build e execucao

Na raiz do projeto:

```bash
mvn clean package
```

Executar exemplos pela IDE (forma mais simples):

- Execute a classe `com.testes.MainL1`
- Execute a classe `com.testes.MainL2`
- Execute a classe `com.testes.MainL1L2`

Executar exemplos via Maven (sem configurar plugin no `pom.xml`):

```bash
mvn -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.testes.MainL1
mvn -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.testes.MainL2
mvn -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.testes.MainL1L2
```

Se voce preferir apenas compilar sem executar exemplos:

```bash
mvn -DskipTests package
```

## Exemplo rapido de uso (L1)

```java
ConfiguracaoCacheMemoria cfgMemoria = new ConfiguracaoCacheMemoria(60, 10_000);
InMemoryCacheApi cacheL1 = new InMemoryCacheApi(cfgMemoria);

cacheL1.put("cliente:123", "Joao");
String nome = cacheL1.get("cliente:123", String.class).orElse("nao encontrado");

cacheL1.close();
```

## Exemplo rapido de uso (L2 Redis)

```java
ConfiguracaoRedis cfgRedis = ConfiguracaoRedis.builder()
		.ambiente("dev")
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

try (RedisCacheApi cacheL2 = new RedisCacheApi(cfgRedis)) {
	cacheL2.put("produto:1", "Notebook", 120);
	String valor = cacheL2.get("produto:1", String.class).orElse("nao encontrado");
	System.out.println(valor);
}
```

## Exemplo rapido de uso (L1 + L2)

```java
InMemoryCacheApi l1 = new InMemoryCacheApi(new ConfiguracaoCacheMemoria(30, 5_000));

ConfiguracaoRedis cfgRedis = ConfiguracaoRedis.builder()
		.ambiente("dev")
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

try (RedisCacheApi l2 = new RedisCacheApi(cfgRedis);
		 L1L2CacheManager cache = new L1L2CacheManager(l1, l2)) {

	cache.put("pedido:999", "status=aprovado", 60);

	String status = cache.get("pedido:999", String.class).orElse("nao encontrado");
	System.out.println(status);
}
```

## Configuracao recomendada para producao

Os valores abaixo sao um ponto de partida. Ajuste conforme volume, SLA e capacidade da sua infraestrutura.

### 1) Prefixo de ambiente

- `ambiente`: use um valor claro por ambiente para evitar colisao de chave.
	- Exemplo: `prod`, `staging`, `homolog`.

### 2) Timeouts de Redis

- `timeout` (ms): 300 a 1000 para APIs de baixa latencia.
	- Exemplo inicial: `500`.

### 3) Pool de conexoes

Para uma aplicacao de medio porte (2 a 8 vCPUs):

- `maxConnections`: 64
- `maxIdleConnections`: 16
- `minIdleConnections`: 8
- `blockWhenExhausted`: true
- `maxWaitMillis`: 50 a 200

Ponto de partida sugerido:

```java
ConfiguracaoRedis cfgProd = ConfiguracaoRedis.builder()
		.ambiente("prod")
		.host("redis-prod.seu-dominio")
		.port(6379)
		.password("definir-via-segredo")
		.database(0)
		.timeout(500)
		.maxConnections(64)
		.maxIdleConnections(16)
		.minIdleConnections(8)
		.maxWaitMillis(100)
		.blockWhenExhausted(true)
		.build();
```

### 4) TTLs

Sugestao por perfil de dado:

- Dados quase estaticos (catalogos, tabelas de referencia): 10 a 60 minutos.
- Sessao ou estado curto: 30 a 300 segundos.
- Resultado de consulta pesada: 60 a 600 segundos.

Boa pratica:

- Use TTL no L2 (`put(key, value, ttlSeconds)`).
- Use TTL mais curto no L1 para reduzir risco de staleness local.

### 5) L1 (Caffeine)

Valores iniciais comuns:

- `ttlSegundos`: 15 a 120
- `tamanhoMaximo`: 10_000 a 200_000

Exemplo para servico de leitura intensa:

```java
ConfiguracaoCacheMemoria cfgL1Prod = new ConfiguracaoCacheMemoria(30, 50_000);
```

## Boas praticas operacionais

- Trate cache como camada de aceleracao, nunca como unica fonte de verdade.
- Monitore hit rate, latencia e erros do Redis.
- Evite objetos gigantes no cache; prefira payloads menores e focados.
- Normalize padrao de chave (exemplo: `entidade:id`).
- Feche recursos no shutdown (`close()` em `RedisCacheApi` e `L1L2CacheManager`).

## Possiveis evolucoes

- Instrumentacao com metricas (Micrometer/Prometheus).
- Politicas de retry e fallback mais detalhadas em falhas de Redis.
- Invalidação em lote por namespace/prefixo.

## Licenca

Defina a licenca do projeto conforme politica do seu time/empresa.
