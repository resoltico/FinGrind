package dev.erst.fingrind.cli;

import java.io.PrintStream;
import java.util.Objects;

/** Creates the explicit output and diagnostics channels used by response-writer tests. */
final class CliTestOutputChannels {
  private CliTestOutputChannels() {}

  static CliOutputChannel forOutput(PrintStream outputStream) {
    return forStreams(outputStream, outputStream);
  }

  static CliOutputChannel forStreams(PrintStream outputStream, PrintStream diagnosticsStream) {
    return new CliOutputChannel(
        Objects.requireNonNull(outputStream, "outputStream"),
        Objects.requireNonNull(diagnosticsStream, "diagnosticsStream"));
  }
}
