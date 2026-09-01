package dev.erst.fingrind.cli;

import java.nio.file.Path;

/** Deterministic no-clobber refusal detected before a PDF publication transaction is opened. */
final class CliPdfOutputTargetOccupiedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient Path outputPath;
  private final String serializedOutputPath;

  CliPdfOutputTargetOccupiedException(Path outputPath) {
    super("The requested PDF destination already exists and will not be overwritten.");
    this.outputPath = CliExceptionPathSnapshot.capture(outputPath);
    this.serializedOutputPath = this.outputPath.toString();
  }

  Path outputPath() {
    return outputPath == null ? CliExceptionPathSnapshot.restore(serializedOutputPath) : outputPath;
  }
}
