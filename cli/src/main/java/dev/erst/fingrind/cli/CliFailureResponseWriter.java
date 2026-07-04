package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.Objects;

/** Writes failure envelopes and text diagnostics through the shared CLI output channel. */
final class CliFailureResponseWriter {
  private final CliOutputChannel outputChannel;

  CliFailureResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeFailure(CliFailure failure, OutputMode outputMode) {
    if (outputMode == OutputMode.JSON) {
      outputChannel.writeDiagnosticEnvelope(CliEnvelopeMapper.failureEnvelope(failure));
      return;
    }
    outputChannel.writeFailureText(CliFailureOutputRenderer.renderFailureText(failure));
  }
}
