package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
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
    writeDocument(diagnosticsStream, CliWireJson.writeJsonBytes(envelope));
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

  void writeMutationRejection(CliEnvelopeJsonModels.RejectedEnvelope envelope) {
    writeDiagnosticEnvelope(envelope);
  }

  void writeQueryRejection(CliEnvelopeJsonModels.RejectedEnvelope envelope) {
    writeDiagnosticEnvelope(envelope);
  }
}
