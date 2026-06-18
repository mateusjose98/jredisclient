package com.testes.redis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.Base64;
import java.util.Objects;

public final class JavaRedisSerializer<T extends Serializable> implements RedisSerializer<T> {

  private final Class<T> type;

  public JavaRedisSerializer(Class<T> type) {
    this.type = Objects.requireNonNull(type, "type");
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

    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
         ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
      objectStream.writeObject(value);
      objectStream.flush();
      return Base64.getEncoder().encodeToString(byteStream.toByteArray());
    } catch (IOException exception) {
      throw new RedisSerializationException("could not serialize object of type " + type.getName(), exception);
    }
  }

  @Override
  public T deserialize(String payload) {
    if (payload == null) {
      return null;
    }

    byte[] rawBytes;
    try {
      rawBytes = Base64.getDecoder().decode(payload);
    } catch (IllegalArgumentException exception) {
      throw new RedisSerializationException("payload is not valid Base64 for type " + type.getName(), exception);
    }

    try (ByteArrayInputStream byteStream = new ByteArrayInputStream(rawBytes);
         ContextAwareObjectInputStream objectStream = new ContextAwareObjectInputStream(byteStream)) {
      Object value = objectStream.readObject();
      return type.cast(value);
    } catch (IOException | ClassNotFoundException | ClassCastException exception) {
      throw new RedisSerializationException("could not deserialize payload to type " + type.getName(), exception);
    }
  }

  private static final class ContextAwareObjectInputStream extends ObjectInputStream {

    private ContextAwareObjectInputStream(ByteArrayInputStream inputStream) throws IOException {
      super(inputStream);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass classDescriptor) throws IOException, ClassNotFoundException {
      String className = classDescriptor.getName();
      ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
      if (contextClassLoader != null) {
        try {
          return Class.forName(className, false, contextClassLoader);
        } catch (ClassNotFoundException ignored) {
        }
      }
      return super.resolveClass(classDescriptor);
    }
  }
}
