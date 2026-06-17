package dev.erst.fingrind.cli;

import java.util.Objects;

/** Writes failure envelopes and text diagnostics through the shared CLI output channel. */
final class CliFailureResponseWriter {
  private final CliOutputChannel outputChannel;

  CliFailureResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeFailure(CliFailure failure) {
    outputChannel.writeDiagnosticEnvelope(CliEnvelopeMapper.failureEnvelope(failure));
  }

  void writeDeterministicFailure(CliFailure failure) {
    outputChannel.writeDiagnosticEnvelope(CliEnvelopeMapper.failureEnvelope(failure));
  }
}
