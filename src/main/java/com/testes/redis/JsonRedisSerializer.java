package com.testes.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Objects;

public final class JsonRedisSerializer<T> implements RedisSerializer<T> {

  private static final ObjectMapper DEFAULT_MAPPER = JsonMapper.builder()
      .addModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
      .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
      .defaultDateFormat(new StdDateFormat().withColonInTimeZone(true))
      .build();

  private final Class<T> type;
  private final ObjectMapper objectMapper;

  public JsonRedisSerializer(Class<T> type) {
    this(type, DEFAULT_MAPPER);
  }

  public JsonRedisSerializer(Class<T> type, ObjectMapper objectMapper) {
    this.type = Objects.requireNonNull(type, "type");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @Override
  public Class<T> targetType() {
    return type;
  }

  @Override
  public String serialize(T value) {
    if (value == null) {
      throw new RedisSerializationException("value must not be null");
    }

    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new RedisSerializationException("could not serialize object to JSON for type " + type.getName(), exception);
    }
  }

  @Override
  public T deserialize(String payload) {
    if (payload == null) {
      return null;
    }

    try {
      return objectMapper.readValue(payload, type);
    } catch (Exception exception) {
      throw new RedisSerializationException("could not deserialize JSON payload to type " + type.getName(), exception);
    }
  }
}
