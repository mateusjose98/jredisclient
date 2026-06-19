package com.testes;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.testes.l1.ConfiguracaoCacheMemoria;
import com.testes.l1.InMemoryCacheApi;
import com.testes.l2.ConfiguracaoRedis;
import com.testes.l2.RedisCacheApi;

class Contrato {
  private String titulo;
  private int paginas;
  private Boolean ativo;
  private LocalDate dataCriacao;
  private Date dataExpiracao;
  private LocalDateTime dataUltimaAtualizacao;
  private List<String> partesEnvolvidas;

  public String getTitulo() {
    return titulo;
  }

  public void setTitulo(String titulo) {
    this.titulo = titulo;
  }

  public int getPaginas() {
    return paginas;
  }

  public void setPaginas(int paginas) {
    this.paginas = paginas;
  }

  public Boolean getAtivo() {
    return ativo;
  }

  public void setAtivo(Boolean ativo) {
    this.ativo = ativo;
  }

  public LocalDate getDataCriacao() {
    return dataCriacao;
  }

  public void setDataCriacao(LocalDate dataCriacao) {
    this.dataCriacao = dataCriacao;
  }

  public Date getDataExpiracao() {
    return dataExpiracao;
  }

  public void setDataExpiracao(Date dataExpiracao) {
    this.dataExpiracao = dataExpiracao;
  }

  public LocalDateTime getDataUltimaAtualizacao() {
    return dataUltimaAtualizacao;
  }

  public void setDataUltimaAtualizacao(LocalDateTime dataUltimaAtualizacao) {
    this.dataUltimaAtualizacao = dataUltimaAtualizacao;
  }

  public List<String> getPartesEnvolvidas() {
    return partesEnvolvidas;
  }

  public void setPartesEnvolvidas(List<String> partesEnvolvidas) {
    this.partesEnvolvidas = partesEnvolvidas;
  }
}

public class MainL1L2 {
  public static void main(String[] args) {

    InMemoryCacheApi memoria = new InMemoryCacheApi(new ConfiguracaoCacheMemoria(60, 1000));
    ConfiguracaoRedis configuracaoRedis = ConfiguracaoRedis.builder()
        .ambiente("testes")
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
    L1L2CacheManager cacheManager = new L1L2CacheManager(memoria, cacheRedis);

    cacheManager.put("chave1", "valor1", 60);
    cacheManager.put("chave2", "valor2", 60);

    System.out.println("Recuperando chave1: " + cacheManager.get("chave1", String.class).orElse("Não encontrado"));
    System.out.println("Recuperando chave2: " + cacheManager.get("chave2", String.class).orElse("Não encontrado"));

    Contrato contrato = new Contrato();
    contrato.setTitulo("Contrato de Prestação de Serviços");
    contrato.setPaginas(10);
    contrato.setAtivo(true);
    contrato.setDataCriacao(LocalDate.of(2024, 6, 6));
    contrato.setDataExpiracao(Date.valueOf(LocalDate.of(2024, 7, 6)));
    contrato.setDataUltimaAtualizacao(LocalDateTime.of(2024, 6, 6, 10, 3, 7));
    contrato.setPartesEnvolvidas(List.of("Empresa A", "Empresa B"));

    cacheManager.put("contrato1", contrato, 120);
    Contrato contratoRecuperado = cacheManager.get("contrato1", Contrato.class).orElse(null);
    if (contratoRecuperado != null) {
      System.out.println("Contrato recuperado:");
      System.out.println("Título: " + contratoRecuperado.getTitulo());
      System.out.println("Páginas: " + contratoRecuperado.getPaginas());
      System.out.println("Ativo: " + contratoRecuperado.getAtivo());
      System.out.println("Data de Criação: " + contratoRecuperado.getDataCriacao());
      System.out.println("Data de Expiração: " + contratoRecuperado.getDataExpiracao());
      System.out.println("Data da Última Atualização: " + contratoRecuperado.getDataUltimaAtualizacao());
      System.out.println("Partes Envolvidas: " + String.join(", ", contratoRecuperado.getPartesEnvolvidas()));
    } else {
      System.out.println("Contrato não encontrado no cache.");
    }

  }

}
