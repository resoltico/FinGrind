package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.io.PrintStream;
import java.util.Objects;

/** Low-level stdout/stderr channel pair for deterministic CLI response rendering. */
final class CliOutputChannel {
  private final PrintStream outputStream;
  private final PrintStream diagnosticsStream;

  CliOutputChannel(PrintStream outputStream) {
    this(outputStream, outputStream);
  }

  CliOutputChannel(PrintStream outputStream, PrintStream diagnosticsStream) {
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
    this.diagnosticsStream = Objects.requireNonNull(diagnosticsStream, "diagnosticsStream");
  }

  void writeJson(Object value) {
    writeDocument(outputStream, CliWireJson.writeJsonBytes(value));
  }

  void writePrettyJson(Object value) {
    writeText(CliWireJson.prettyJsonText(value));
  }

  void writeDiagnosticEnvelope(Record envelope) {
    Record resolvedEnvelope =
        envelope instanceof CliEnvelopeJsonModels.Envelope<?> typedEnvelope
            ? CliEnvelopeMapper.withFailurePaths(typedEnvelope)
            : envelope;
    writeDocument(diagnosticsStream, CliWireJson.writeJsonBytes(resolvedEnvelope));
  }

  private static void writeDocument(PrintStream stream, byte[] document) {
    stream.write(document, 0, document.length);
    stream.println();
    stream.flush();
  }

  void writeText(String value) {
    outputStream.print(value);
    outputStream.println();
    outputStream.flush();
  }

  void writeFailureText(String value) {
    diagnosticsStream.print(value);
    diagnosticsStream.println();
    diagnosticsStream.flush();
  }

  void writeEnvelope(Record envelope) {
    writeJson(envelope);
  }

  void writeSuccess(ProtocolSuccessPayload payload) {
    writeEnvelope(CliEnvelopeMapper.successEnvelope(payload));
  }

  void writeMutationRejection(CliEnvelopeJsonModels.Envelope<?> envelope) {
    writeDiagnosticEnvelope(envelope);
  }

  void writeQueryRejection(CliEnvelopeJsonModels.Envelope<?> envelope) {
    writeDiagnosticEnvelope(envelope);
  }

  void writeRejectedEnvelope(
      CliEnvelopeJsonModels.Envelope<?> envelope, OutputMode requestedOutputMode) {
    if (requestedOutputMode == OutputMode.JSON) {
      writeDiagnosticEnvelope(envelope);
      return;
    }
    String code = Objects.requireNonNull(envelope.code(), "code");
    String message = Objects.requireNonNull(envelope.message(), "message");
    writeFailureText(
        CliFailureOutputRenderer.renderRejectedText(
            code,
            message,
            envelope.hint(),
            envelope.idempotencyKey(),
            (CliRejectionJsonModels.RejectionDetails) envelope.details()));
  }
}
