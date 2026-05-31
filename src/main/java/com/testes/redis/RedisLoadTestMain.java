package com.testes.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public final class RedisLoadTestMain {

  private RedisLoadTestMain() {
  }

  public static void main(String[] args) throws InterruptedException {
    int threads = Integer.getInteger("load.threads", 16);
    int iterationsPerThread = Integer.getInteger("load.iterations", 20_000);
    Duration ttl = Duration.ofSeconds(Long.getLong("load.ttl.seconds", 60L));

    RedisClientConfig config = RedisClientConfig.builder()
        .host(System.getProperty("redis.host", "localhost"))
        .port(Integer.getInteger("redis.port", 6379))
        .defaultTtl(ttl)
        .build();

    LongAdder successfulWrites = new LongAdder();
    LongAdder successfulReads = new LongAdder();
    LongAdder successfulDeletes = new LongAdder();

    long start = System.nanoTime();

    try (RedisCacheClient cacheClient = RedisClients.create(config)) {
      ExecutorService executorService = Executors.newFixedThreadPool(threads);
      CountDownLatch latch = new CountDownLatch(threads);
      List<Runnable> workers = new ArrayList<>(threads);

      for (int threadIndex = 0; threadIndex < threads; threadIndex++) {
        final int workerId = threadIndex;
        workers.add(() -> {
          try {
            for (int iteration = 0; iteration < iterationsPerThread; iteration++) {
              String key = "load:" + workerId + ':' + iteration;
              String value = "v-" + ThreadLocalRandom.current().nextInt(1_000_000);

              if (cacheClient.set(key, value, ttl)) {
                successfulWrites.increment();
              }
              if (value.equals(cacheClient.getOrDefault(key, ""))) {
                successfulReads.increment();
              }
              // if (cacheClient.delete(key)) {
              // successfulDeletes.increment();
              // }
            }
          } finally {
            latch.countDown();
          }
        });
      }

      for (Runnable worker : workers) {
        executorService.submit(worker);
      }

      latch.await();
      executorService.shutdown();
      executorService.awaitTermination(1, TimeUnit.MINUTES);
    }

    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    long totalOperations = (long) threads * iterationsPerThread * 3L;

    System.out.println("Teste de carga finalizado");
    System.out.println("threads=" + threads);
    System.out.println("iteraçõesPorThread=" + iterationsPerThread);
    System.out.println("tempoTotalEmMs=" + elapsedMs);
    System.out.println("Operações solicitadas=" + totalOperations);
    System.out.println("Gravações com sucesso=" + successfulWrites.sum());
    System.out.println("Leituras com sucesso=" + successfulReads.sum());
    System.out.println("Exclusões com sucesso=" + successfulDeletes.sum());
  }
}