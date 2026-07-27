package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.PrintStream;

/** Focused test fixture for the failure response writer and its diagnostics routing. */
final class CliFailureResponseWriterFixture {
  private final CliFailureResponseWriter writer;

  CliFailureResponseWriterFixture(PrintStream outputStream) {
    writer = new CliFailureResponseWriter(CliTestOutputChannels.forOutput(outputStream));
  }

  CliFailureResponseWriterFixture(PrintStream outputStream, PrintStream diagnosticsStream) {
    writer =
        new CliFailureResponseWriter(
            CliTestOutputChannels.forStreams(outputStream, diagnosticsStream));
  }

  void writeFailure(CliFailure failure) {
    writer.writeFailure(failure, OutputMode.JSON);
  }

  void writeFailure(String code, String message) {
    writeFailure(new CliFailure(code, message, null, null));
  }
}
