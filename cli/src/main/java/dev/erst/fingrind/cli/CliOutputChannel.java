package dev.erst.fingrind.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.PrintStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

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
    byte[] document = writer.writeValueAsBytes(value);
    outputStream.write(document, 0, document.length);
    outputStream.println();
    outputStream.flush();
  }

  void writeText(String value) {
    outputStream.print(value);
    outputStream.println();
    outputStream.flush();
  }

  void writeEnvelope(Object envelope) {
    writeJson(envelope);
  }

  private void writePrettyEnvelope(Object envelope) {
    writePrettyJson(envelope);
  }

  void writePrettySuccess(Object payload) {
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
        .build();
  }
}
