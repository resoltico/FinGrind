package dev.erst.fingrind.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.PrintStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Low-level JSON and text output channel for deterministic CLI response rendering. */
final class CliOutputChannel {
  private final ObjectMapper objectMapper = configuredObjectMapper();
  private final PrintStream outputStream;

  CliOutputChannel(PrintStream outputStream) {
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
  }

  void writeJson(Object value, boolean pretty) {
    byte[] document =
        pretty
            ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value)
            : objectMapper.writeValueAsBytes(value);
    outputStream.write(document, 0, document.length);
    outputStream.println();
    outputStream.flush();
  }

  void writeText(String value) {
    outputStream.print(value);
    outputStream.println();
    outputStream.flush();
  }

  void writeEnvelope(Object envelope, boolean pretty) {
    writeJson(envelope, pretty);
  }

  void writeSuccess(Object payload, boolean pretty) {
    writeEnvelope(CliResponsePayloadMapper.successEnvelope(payload), pretty);
  }

  void writeMutationRejection(
      OutputMode outputMode,
      CliResponseJsonModels.RejectedEnvelope envelope,
      @Nullable String idempotencyKey) {
    if (outputMode == OutputMode.HUMAN) {
      writeText(
          CliFailureOutputRenderer.renderRejectedHuman(
              envelope.code(), envelope.message(), idempotencyKey));
      return;
    }
    writeEnvelope(envelope, false);
  }

  void writeQueryRejection(OutputMode outputMode, CliResponseJsonModels.RejectedEnvelope envelope) {
    outputMode.run(
        () -> writeEnvelope(envelope, false),
        () ->
            writeText(
                CliFailureOutputRenderer.renderRejectedHuman(
                    envelope.code(), envelope.message(), envelope.idempotencyKey())),
        () -> writeEnvelope(envelope, false));
  }

  private static ObjectMapper configuredObjectMapper() {
    return JsonMapper.builder()
        .changeDefaultPropertyInclusion(
            value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
        .build();
  }
}
