package dev.erst.fingrind.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.core.WireValue;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/** Shared CLI JSON serialization utilities for public output and help-template rendering. */
final class CliWireJson {
  private static final ObjectMapper OUTPUT_OBJECT_MAPPER = configuredOutputObjectMapper();
  private static final ObjectWriter WRITER = OUTPUT_OBJECT_MAPPER.writer();
  private static final ObjectWriter PRETTY_WRITER =
      OUTPUT_OBJECT_MAPPER.writerWithDefaultPrettyPrinter();

  private CliWireJson() {}

  static byte[] writeJsonBytes(Object value) {
    return writeBytes(WRITER, value);
  }

  static byte[] writePrettyJsonBytes(Object value) {
    return writeBytes(PRETTY_WRITER, value);
  }

  static String prettyJsonText(Object value) {
    try {
      return PRETTY_WRITER.writeValueAsString(value);
    } catch (RuntimeException exception) {
      throw unwrapWireValueFailure(exception);
    }
  }

  static String jsonText(Object value) {
    try {
      return WRITER.writeValueAsString(value);
    } catch (RuntimeException exception) {
      throw unwrapWireValueFailure(exception);
    }
  }

  private static byte[] writeBytes(ObjectWriter writer, Object value) {
    try {
      return writer.writeValueAsBytes(value);
    } catch (RuntimeException exception) {
      throw unwrapWireValueFailure(exception);
    }
  }

  private static ObjectMapper configuredOutputObjectMapper() {
    return JsonMapper.builder()
        .changeDefaultPropertyInclusion(
            value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
        .addModule(finGrindEnumModule())
        .build();
  }

  private static SimpleModule finGrindEnumModule() {
    return new SimpleModule("finGrindEnumWireValues").addSerializer(new FinGrindEnumSerializer());
  }

  private static RuntimeException unwrapWireValueFailure(RuntimeException exception) {
    WireValueSerializationException wireValueFailure = findWireValueFailure(exception);
    return wireValueFailure == null ? exception : wireValueFailure;
  }

  private static @Nullable WireValueSerializationException findWireValueFailure(
      Throwable exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof WireValueSerializationException wireValueFailure) {
        return wireValueFailure;
      }
      current = current.getCause();
    }
    return null;
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

  private static String wireValue(Enum<?> value) {
    Objects.requireNonNull(value, "value");
    Class<?> enumType = value.getDeclaringClass();
    if (value instanceof WireValue wireValue) {
      try {
        String serialized = wireValue.wireValue();
        if (serialized != null && !serialized.isBlank()) {
          return serialized;
        }
        throw new WireValueSerializationException(
            "CLI JSON wireValue() must return a non-blank String for " + enumType.getName() + ".");
      } catch (WireValueSerializationException exception) {
        throw exception;
      } catch (RuntimeException exception) {
        throw new WireValueSerializationException(
            "Failed to resolve CLI JSON wireValue() for " + enumType.getName() + ".", exception);
      }
    }
    if (enumType.getPackageName().startsWith("dev.erst.fingrind.")) {
      throw new WireValueSerializationException(
          "FinGrind enum " + enumType.getName() + " must implement WireValue for CLI JSON.");
    }
    return value.name();
  }

  /** Dedicated failure type for invalid FinGrind enum wire-value serialization contracts. */
  static final class WireValueSerializationException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private WireValueSerializationException(String message) {
      super(message);
    }

    private WireValueSerializationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
