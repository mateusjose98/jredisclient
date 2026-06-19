package com.testes;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.testes.l1.ConfiguracaoCacheMemoria;
import com.testes.l1.InMemoryCacheApi;

class Endereco implements java.io.Serializable {
  private String rua;
  private String cidade;
  private String estado;
  private String cep;

  public Endereco(String rua, String cidade, String estado, String cep) {
    this.rua = rua;
    this.cidade = cidade;
    this.estado = estado;
    this.cep = cep;
  }

  public String getRua() {
    return rua;
  }

  public String getCidade() {
    return cidade;
  }

  public String getEstado() {
    return estado;
  }

  public String getCep() {
    return cep;
  }

  @Override
  public String toString() {
    return "Endereco{" +
        "rua='" + rua + '\'' +
        ", cidade='" + cidade + '\'' +
        ", estado='" + estado + '\'' +
        ", cep='" + cep + '\'' +
        '}';
  }

}

class Pessoa implements java.io.Serializable {
  private String nome;
  private int idade;
  private LocalDate dataNascimento;
  private Endereco endereco;

  public Pessoa(String nome, int idade, LocalDate dataNascimento, Endereco endereco) {
    this.nome = nome;
    this.idade = idade;
    this.dataNascimento = dataNascimento;
    this.endereco = endereco;
  }

  public String getNome() {
    return nome;
  }

  public int getIdade() {
    return idade;
  }

  public LocalDate getDataNascimento() {
    return dataNascimento;
  }

  public Endereco getEndereco() {
    return endereco;
  }

  @Override
  public String toString() {
    return "Pessoa{" +

        "nome='" + nome + '\'' +
        ", idade=" + idade +
        ", dataNascimento=" + dataNascimento +
        ", endereco=" + endereco +
        '}';

  }

}

public final class MainL1 {

  public static void main(String[] args) throws Exception {

    ConfiguracaoCacheMemoria configuracao = new ConfiguracaoCacheMemoria(60, 1000);

    InMemoryCacheApi cacheEmMemoria = new InMemoryCacheApi(configuracao);

    cacheEmMemoria.put("pessoa1", new Pessoa("João", 30, LocalDate.of(1993, 5, 15),
        new Endereco("Rua A", "Cidade X", "Estado Y", "12345-678")));

    cacheEmMemoria.put("query1", "SELECT * FROM tabela");

    Optional<Pessoa> pessoaOpt = cacheEmMemoria.get("pessoa1", Pessoa.class);
    pessoaOpt.ifPresent(pessoa -> System.out.println("Pessoa recuperada do cache: " + pessoa));

    Optional<String> queryOpt = cacheEmMemoria.get("query1", String.class);
    queryOpt.ifPresent(query -> System.out.println("Query recuperada do cache: " + query));

  }

}
