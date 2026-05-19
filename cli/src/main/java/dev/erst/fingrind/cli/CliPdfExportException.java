package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Path;
import java.util.Objects;

/** Runtime failure raised when FinGrind cannot write one requested PDF report artifact. */
final class CliPdfExportException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final Path outputPath;

  CliPdfExportException(Path outputPath, Exception cause) {
    super(
        "Failed to write PDF export to "
            + PublicPathHint.fromPath(Objects.requireNonNull(outputPath, "outputPath")).value()
            + ".",
        Objects.requireNonNull(cause, "cause"));
    this.outputPath = outputPath;
  }

  Path outputPath() {
    return outputPath;
  }
}
