package com.testes.l2;

import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonCacheSerializer {

  private final ObjectMapper objectMapper;
  private final Logger LOGGER = Logger.getLogger(JacksonCacheSerializer.class.getName());

  public JacksonCacheSerializer() {
    this(new ObjectMapper());
  }

  public JacksonCacheSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.objectMapper.findAndRegisterModules();
  }

  public <V> byte[] serialize(V value) {
    try {
      return objectMapper.writeValueAsBytes(value);
    } catch (Exception e) {
      LOGGER.severe("Erro ao serializar objeto para cache: " + e.getMessage());
      return null;
    }
  }

  public <V> V deserialize(byte[] bytes, Class<V> type) {
    try {
      return objectMapper.readValue(bytes, type);
    } catch (Exception e) {
      LOGGER.severe("Erro ao desserializar bytes do cache: " + e.getMessage());
      return null;
    }
  }

}
