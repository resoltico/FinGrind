package dev.erst.fingrind.cli;

import java.io.PrintStream;

/**
 * Test-only façade that preserves legacy writer-fixture ergonomics while production code uses
 * narrower response writers directly.
 */
final class CliResponseWriter extends CliResponseWriterPlanSupport {

  CliResponseWriter(PrintStream outputStream) {
    super(outputStream);
  }
}
