package dev.erst.fingrind.cli;

import java.nio.file.Path;
import java.util.Objects;

/** Runtime failure raised when FinGrind cannot write one requested PDF report artifact. */
final class CliPdfExportException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient Path outputPath;
  private final String serializedOutputPath;

  CliPdfExportException(Path outputPath, Exception cause) {
    super("Failed to write the PDF export.", Objects.requireNonNull(cause, "cause"));
    this.outputPath = CliExceptionPathSnapshot.capture(outputPath);
    this.serializedOutputPath = this.outputPath.toString();
  }

  Path outputPath() {
    return outputPath == null ? CliExceptionPathSnapshot.restore(serializedOutputPath) : outputPath;
  }
}
