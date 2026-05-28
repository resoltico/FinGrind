package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolDiagnosticCode;
import java.io.PrintStream;
import java.util.Objects;

/** Writes non-fatal operator diagnostics to the auxiliary CLI stream. */
final class CliDiagnosticsWriter {
  private static final String PDF_EXPORT_INFO_MESSAGE_PREFIX =
      "Wrote the requested PDF report artifact to ";

  private final PrintStream diagnosticsStream;

  CliDiagnosticsWriter(PrintStream diagnosticsStream) {
    this.diagnosticsStream = Objects.requireNonNull(diagnosticsStream, "diagnosticsStream");
  }

  void writePdfExportInfo(java.nio.file.Path outputPath) {
    Objects.requireNonNull(outputPath, "outputPath");
    diagnosticsStream.print(
        CliFailureOutputRenderer.renderInfoText(
            new CliFailure(
                ProtocolDiagnosticCode.PDF_EXPORTED.wireValue(),
                PDF_EXPORT_INFO_MESSAGE_PREFIX + CliPublicPaths.redactedValue(outputPath),
                null,
                "--pdf-out")));
    diagnosticsStream.println();
    diagnosticsStream.flush();
  }

  void writeInternalError(String errorId, Throwable throwable) {
    Objects.requireNonNull(errorId, "errorId");
    Objects.requireNonNull(throwable, "throwable");
    diagnosticsStream.println("[internal-error] " + errorId);
    throwable.printStackTrace(diagnosticsStream);
    diagnosticsStream.flush();
  }
}
