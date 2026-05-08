package dev.erst.fingrind.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.core.WireValue;
import java.io.PrintStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/** Low-level JSON and text output channel for deterministic CLI response rendering. */
final class CliOutputChannel {
  private final ObjectMapper objectMapper = configuredObjectMapper();
  private final PrintStream outputStream;

  CliOutputChannel(PrintStream outputStream) {
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
  }

  void writeJson(Object value) {
    writeJsonDocument(objectMapper.writer(), value);
  }

  void writePrettyJson(Object value) {
    writeJsonDocument(objectMapper.writerWithDefaultPrettyPrinter(), value);
  }

  private void writeJsonDocument(ObjectWriter writer, Object value) {
    byte[] document;
    try {
      document = writer.writeValueAsBytes(value);
    } catch (RuntimeException exception) {
      throw unwrapWireValueFailure(exception);
    }
    outputStream.write(document, 0, document.length);
    outputStream.println();
    outputStream.flush();
  }

  void writeText(String value) {
    outputStream.print(value);
    outputStream.println();
    outputStream.flush();
  }

  void writeEnvelope(Record envelope) {
    writeJson(envelope);
  }

  private void writePrettyEnvelope(Record envelope) {
    writePrettyJson(envelope);
  }

  void writePrettySuccess(ProtocolSuccessPayload payload) {
    writePrettyEnvelope(CliResponsePayloadMapper.successEnvelope(payload));
  }

  void writeMutationRejection(
      OutputMode outputMode,
      CliEnvelopeJsonModels.RejectedEnvelope envelope,
      @Nullable String idempotencyKey) {
    if (outputMode == OutputMode.HUMAN) {
      writeText(
          CliFailureOutputRenderer.renderRejectedHuman(
              envelope.code(), envelope.message(), idempotencyKey));
      return;
    }
    writeEnvelope(envelope);
  }

  void writeQueryRejection(OutputMode outputMode, CliEnvelopeJsonModels.RejectedEnvelope envelope) {
    outputMode.run(
        () -> writeEnvelope(envelope),
        () ->
            writeText(
                CliFailureOutputRenderer.renderRejectedHuman(
                    envelope.code(), envelope.message(), envelope.idempotencyKey())),
        () -> writeEnvelope(envelope));
  }

  private static ObjectMapper configuredObjectMapper() {
    return JsonMapper.builder()
        .changeDefaultPropertyInclusion(
            value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
        .addModule(finGrindEnumModule())
        .build();
  }

  private static SimpleModule finGrindEnumModule() {
    return new SimpleModule("finGrindEnumWireValues").addSerializer(new FinGrindEnumSerializer());
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

  /** Dedicated failure type for invalid FinGrind enum wire-value serialization contracts. */
  private static final class WireValueSerializationException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private WireValueSerializationException(String message) {
      super(message);
    }

    private WireValueSerializationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
