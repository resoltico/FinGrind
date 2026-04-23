package dev.erst.fingrind.cli;

import java.io.PrintStream;
import java.util.Objects;

/** Writes non-fatal operator diagnostics to the auxiliary CLI stream. */
final class CliDiagnosticsWriter {
  private static final String PDF_EXPORT_WARNING_CODE = "pdf-export-warning";
  private static final String PDF_EXPORT_WARNING_HINT =
      "Inspect the selected --pdf-out destination, its parent directory permissions, and the available filesystem space, then rerun the command if you still need the PDF artifact.";

  private final PrintStream diagnosticsStream;

  CliDiagnosticsWriter(PrintStream diagnosticsStream) {
    this.diagnosticsStream = Objects.requireNonNull(diagnosticsStream, "diagnosticsStream");
  }

  void writePdfExportWarning(RuntimeException exception) {
    Objects.requireNonNull(exception, "exception");
    String message =
        Objects.requireNonNullElse(
            exception.getMessage(), "Failed to render or write the requested PDF report artifact.");
    diagnosticsStream.print(
        CliFailureOutputRenderer.renderWarningHuman(
            new CliFailure(
                PDF_EXPORT_WARNING_CODE, message, PDF_EXPORT_WARNING_HINT, "--pdf-out")));
    diagnosticsStream.println();
    diagnosticsStream.flush();
  }
}
