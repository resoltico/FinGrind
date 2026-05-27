package dev.erst.fingrind.jazzer.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/** Writes stable public failure output for the local Jazzer operator CLI. */
final class JazzerCliFailureWriter {
  private JazzerCliFailureWriter() {}

  static void writeFailure(
      PrintWriter outputWriter,
      PrintWriter errorWriter,
      boolean jsonOutputRequested,
      JazzerCliCommandFailurePayload failure)
      throws IOException {
    if (jsonOutputRequested) {
      outputWriter.println(JazzerJson.toJson(failure));
      return;
    }
    errorWriter.println(failure.message());
    errorWriter.println();
    errorWriter.print(failure.usage());
  }

  static String failureMessage(IllegalArgumentException exception) {
    return Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName());
  }
}
