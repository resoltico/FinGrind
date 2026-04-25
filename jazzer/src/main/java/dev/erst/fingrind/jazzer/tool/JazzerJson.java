package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.core.WireValue;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/** Centralizes JSON serialization for the local Jazzer operator layer. */
public final class JazzerJson {
  private static final JsonMapper JSON_MAPPER =
      JsonMapper.builder()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .addModule(finGrindEnumModule())
          .build();

  private JazzerJson() {}

  /** Writes one value as pretty-printed JSON to the requested path. */
  public static void write(Path path, Object value) throws IOException {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(value, "value must not be null");
    JSON_MAPPER.writeValue(path.toFile(), value);
  }

  /** Reads one JSON value from disk into the requested type. */
  public static <T> T read(Path path, Class<T> type) throws IOException {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(type, "type must not be null");
    return JSON_MAPPER.readValue(path.toFile(), type);
  }

  /** Reads one JSON resource from the classpath into the requested type. */
  public static <T> T readResource(String resourcePath, Class<T> type) throws IOException {
    Objects.requireNonNull(resourcePath, "resourcePath must not be null");
    Objects.requireNonNull(type, "type must not be null");
    try (InputStream resourceStream = JazzerJson.class.getResourceAsStream(resourcePath)) {
      if (resourceStream == null) {
        throw new IOException("Missing classpath resource: " + resourcePath);
      }
      return JSON_MAPPER.readValue(resourceStream, type);
    }
  }

  /** Returns one value as pretty-printed JSON text. */
  public static String toJson(Object value) throws IOException {
    Objects.requireNonNull(value, "value must not be null");
    return JSON_MAPPER.writeValueAsString(value);
  }

  private static SimpleModule finGrindEnumModule() {
    return new SimpleModule("finGrindEnumWireValues")
        .addSerializer(new FinGrindEnumSerializer())
        .addDeserializer(Enum.class, new FinGrindEnumDeserializer());
  }

  /** Serializer that forces FinGrind-owned enums onto their explicit wire values. */
  private static final class FinGrindEnumSerializer extends ValueSerializer<Enum<?>> {
    @Override
    public void serialize(
        Enum<?> value, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
      jsonGenerator.writeString(wireValue(value));
    }

    @Override
    public Class<?> handledType() {
      return Enum.class;
    }
  }

  /** Deserializer that reads FinGrind-owned enums back through their wire-value vocabulary. */
  private static final class FinGrindEnumDeserializer extends ValueDeserializer<Enum<?>> {
    private final @Nullable Class<?> enumType;

    private FinGrindEnumDeserializer() {
      enumType = null;
    }

    private FinGrindEnumDeserializer(@Nullable Class<?> enumType) {
      this.enumType = enumType;
    }

    @Override
    public ValueDeserializer<?> createContextual(
        DeserializationContext deserializationContext, BeanProperty property) {
      Class<?> contextualEnumType = contextualEnumType(deserializationContext, property);
      return contextualEnumType == null ? this : new FinGrindEnumDeserializer(contextualEnumType);
    }

    @Override
    public Enum<?> deserialize(
        JsonParser jsonParser, DeserializationContext deserializationContext) {
      Class<?> targetType = requireEnumType(deserializationContext);
      String text = jsonParser.getValueAsString();
      if (text == null) {
        return (Enum<?>) deserializationContext.handleUnexpectedToken(targetType, jsonParser);
      }
      return parseEnum(targetType, text, deserializationContext);
    }

    @Override
    public Class<?> handledType() {
      return Enum.class;
    }

    private Class<?> requireEnumType(DeserializationContext deserializationContext) {
      if (enumType != null) {
        return enumType;
      }
      Class<?> contextualEnumType = contextualEnumType(deserializationContext);
      if (contextualEnumType == null) {
        throw new IllegalStateException("Unable to resolve enum target type for Jazzer JSON.");
      }
      return contextualEnumType;
    }

    private static @Nullable Class<?> contextualEnumType(
        DeserializationContext deserializationContext, @Nullable BeanProperty property) {
      if (property != null && property.getType().getRawClass().isEnum()) {
        return property.getType().getRawClass();
      }
      return contextualEnumType(deserializationContext);
    }

    private static @Nullable Class<?> contextualEnumType(
        DeserializationContext deserializationContext) {
      if (deserializationContext.getContextualType() != null
          && deserializationContext.getContextualType().getRawClass().isEnum()) {
        return deserializationContext.getContextualType().getRawClass();
      }
      return null;
    }
  }

  private static String wireValue(Enum<?> value) {
    Objects.requireNonNull(value, "value");
    Class<?> enumType = value.getDeclaringClass();
    if (value instanceof WireValue wireValue) {
      String serialized = wireValue.wireValue();
      if (serialized != null && !serialized.isBlank()) {
        return serialized;
      }
      throw new IllegalStateException(
          "Jazzer JSON wireValue() must return a non-blank String for " + enumType.getName() + ".");
    }
    if (enumType.getPackageName().startsWith("dev.erst.fingrind.")) {
      throw new IllegalStateException(
          "FinGrind enum " + enumType.getName() + " must implement WireValue for Jazzer JSON.");
    }
    return value.name();
  }

  private static Enum<?> parseEnum(
      Class<?> enumType, String text, DeserializationContext deserializationContext) {
    if (WireValue.class.isAssignableFrom(enumType)) {
      return parseWireValueEnum(enumType, text, deserializationContext);
    }
    if (enumType.getPackageName().startsWith("dev.erst.fingrind.")) {
      return (Enum<?>)
          deserializationContext.handleWeirdStringValue(
              enumType,
              text,
              "FinGrind enum %s must implement WireValue for Jazzer JSON.",
              enumType.getName());
    }
    for (Object constant : enumType.getEnumConstants()) {
      Enum<?> enumConstant = (Enum<?>) constant;
      if (enumConstant.name().equals(text)) {
        return enumConstant;
      }
    }
    return (Enum<?>)
        deserializationContext.handleWeirdStringValue(
            enumType, text, "Unsupported enum value for %s", enumType.getName());
  }

  private static Enum<?> parseWireValueEnum(
      Class<?> enumType, String wireValue, DeserializationContext deserializationContext) {
    try {
      Method fromWireValue = enumType.getMethod("fromWireValue", String.class);
      Object resolved = fromWireValue.invoke(null, wireValue);
      if (resolved instanceof Enum<?> enumValue) {
        return enumValue;
      }
      throw new IllegalStateException(
          "Jazzer JSON fromWireValue(String) must return an enum value for "
              + enumType.getName()
              + ".");
    } catch (NoSuchMethodException exception) {
      return (Enum<?>)
          deserializationContext.handleWeirdStringValue(
              enumType,
              wireValue,
              "FinGrind WireValue enum %s must declare static fromWireValue(String).",
              enumType.getName());
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        return (Enum<?>)
            deserializationContext.handleWeirdStringValue(
                enumType, wireValue, runtimeException.getMessage());
      }
      throw new IllegalStateException(
          "Failed to decode Jazzer JSON wire value for " + enumType.getName() + ".", cause);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException(
          "Unable to access Jazzer JSON fromWireValue(String) for " + enumType.getName() + ".",
          exception);
    }
  }
}
