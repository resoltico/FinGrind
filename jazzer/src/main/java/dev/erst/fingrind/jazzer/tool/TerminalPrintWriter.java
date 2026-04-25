package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Print writer for process streams that flushes on close without closing the underlying terminal.
 */
final class TerminalPrintWriter extends PrintWriter {
  TerminalPrintWriter(OutputStream outputStream) {
    super(
        new BufferedWriter(
            new OutputStreamWriter(
                Objects.requireNonNull(outputStream, "outputStream must not be null"), UTF_8)),
        true);
  }

  @Override
  public void close() {
    flush();
  }
}
