package com.testes.redis;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisSerializerTest {

  @Test
  void shouldRoundTripWithJavaSerializer() {
    JavaRedisSerializer<SerializablePayload> serializer = new JavaRedisSerializer<>(SerializablePayload.class);
    SerializablePayload payload = new SerializablePayload("produto", 2);

    String serialized = serializer.serialize(payload);
    SerializablePayload deserialized = serializer.deserialize(serialized);

    assertEquals(payload, deserialized);
  }

  @Test
  void shouldRejectInvalidBase64InJavaSerializer() {
    JavaRedisSerializer<SerializablePayload> serializer = new JavaRedisSerializer<>(SerializablePayload.class);

    RedisSerializationException exception = assertThrows(
        RedisSerializationException.class,
        () -> serializer.deserialize("not-base64"));

    assertTrue(exception.getMessage().contains("not valid Base64"));
  }

  @Test
  void shouldRoundTripDatesWithJsonSerializer() {
    JsonRedisSerializer<DatedPayload> serializer = new JsonRedisSerializer<>(DatedPayload.class);
    LocalDateTime generatedAt = LocalDateTime.of(2026, 6, 17, 21, 0, 0);
    DatedPayload payload = new DatedPayload(
        "pedido",
        LocalDate.of(2026, 6, 17),
        generatedAt,
        Date.from(generatedAt.toInstant(ZoneOffset.UTC)));

    String serialized = serializer.serialize(payload);
    DatedPayload deserialized = serializer.deserialize(serialized);

    assertEquals(payload, deserialized);
  }

  @Test
  void shouldRejectInvalidJsonPayload() {
    JsonRedisSerializer<DatedPayload> serializer = new JsonRedisSerializer<>(DatedPayload.class);

    assertThrows(RedisSerializationException.class, () -> serializer.deserialize("{broken-json"));
  }

  @Test
  void shouldExposeFactorySerializers() {
    RedisSerializer<String> stringSerializer = RedisSerializers.string();
    RedisSerializer<DatedPayload> jsonSerializer = RedisSerializers.json(DatedPayload.class);
    RedisSerializer<SerializablePayload> javaSerializer = RedisSerializers.java(SerializablePayload.class);

    assertSame(String.class, stringSerializer.targetType());
    assertSame(DatedPayload.class, jsonSerializer.targetType());
    assertSame(SerializablePayload.class, javaSerializer.targetType());
    assertEquals("abc", stringSerializer.serialize("abc"));
    assertEquals("abc", stringSerializer.deserialize("abc"));
    assertNull(stringSerializer.deserialize(null));
  }

  @Test
  void shouldExposeSerializationExceptionMessage() {
    RedisSerializationException exception = new RedisSerializationException("boom");

    assertEquals("boom", exception.getMessage());
  }

  private static final class SerializablePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final int version;

    private SerializablePayload(String name, int version) {
      this.name = name;
      this.version = version;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof SerializablePayload)) {
        return false;
      }
      SerializablePayload that = (SerializablePayload) other;
      return version == that.version && name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode() * 31 + version;
    }
  }

  private static final class DatedPayload {
    private String id;
    private LocalDate businessDate;
    private LocalDateTime generatedAt;
    private Date legacyDate;

    public DatedPayload() {
    }

    private DatedPayload(String id, LocalDate businessDate, LocalDateTime generatedAt, Date legacyDate) {
      this.id = id;
      this.businessDate = businessDate;
      this.generatedAt = generatedAt;
      this.legacyDate = legacyDate;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public LocalDate getBusinessDate() {
      return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
      this.businessDate = businessDate;
    }

    public LocalDateTime getGeneratedAt() {
      return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
      this.generatedAt = generatedAt;
    }

    public Date getLegacyDate() {
      return legacyDate;
    }

    public void setLegacyDate(Date legacyDate) {
      this.legacyDate = legacyDate;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof DatedPayload)) {
        return false;
      }
      DatedPayload payload = (DatedPayload) other;
      return id.equals(payload.id)
          && businessDate.equals(payload.businessDate)
          && generatedAt.equals(payload.generatedAt)
          && legacyDate.equals(payload.legacyDate);
    }

    @Override
    public int hashCode() {
      int result = id.hashCode();
      result = 31 * result + businessDate.hashCode();
      result = 31 * result + generatedAt.hashCode();
      result = 31 * result + legacyDate.hashCode();
      return result;
    }
  }
}
