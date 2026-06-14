package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.io.PrintStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Low-level JSON and text output channel for deterministic CLI response rendering. */
final class CliOutputChannel {
  private final PrintStream outputStream;

  CliOutputChannel(PrintStream outputStream) {
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
  }

  void writeJson(Object value) {
    writeDocument(CliWireJson.writeJsonBytes(value));
  }

  private void writeDocument(byte[] document) {
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

  void writeSuccess(ProtocolSuccessPayload payload) {
    writeEnvelope(CliEnvelopeMapper.successEnvelope(payload));
  }

  void writeMutationRejection(
      OutputMode outputMode,
      CliEnvelopeJsonModels.RejectedEnvelope envelope,
      @Nullable String idempotencyKey) {
    if (outputMode == OutputMode.TEXT) {
      writeText(
          CliFailureOutputRenderer.renderRejectedText(
              envelope.code(),
              envelope.message(),
              envelope.hint(),
              idempotencyKey,
              envelope.details()));
      return;
    }
    writeEnvelope(envelope);
  }

  void writeQueryRejection(OutputMode outputMode, CliEnvelopeJsonModels.RejectedEnvelope envelope) {
    outputMode.run(
        () -> writeEnvelope(envelope),
        () ->
            writeText(
                CliFailureOutputRenderer.renderRejectedText(
                    envelope.code(),
                    envelope.message(),
                    envelope.hint(),
                    envelope.idempotencyKey(),
                    envelope.details())),
        () -> writeEnvelope(envelope));
  }
}
