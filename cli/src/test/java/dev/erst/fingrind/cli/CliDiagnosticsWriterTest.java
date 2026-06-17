package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.ProtocolDiagnosticCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Focused coverage for operator diagnostics emitted outside the main response stream. */
class CliDiagnosticsWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writePdfExportInfo_rendersOneInfoBlockWithTheArtifactPath() {
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();

    new CliDiagnosticsWriter(utf8PrintStream(diagnosticsStream))
        .writePdfExportInfo(Path.of("reports/entity.pdf"));

    String rendered = diagnosticsStream.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Info"));
    assertTrue(rendered.contains(ProtocolDiagnosticCode.PDF_EXPORTED.wireValue()));
    assertTrue(rendered.contains("--pdf-out"));
    assertTrue(rendered.contains("entity.pdf"));
  }

  @Test
  void writeInternalError_rendersTheErrorBannerAndStackTrace() {
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();

    new CliDiagnosticsWriter(utf8PrintStream(diagnosticsStream))
        .writeInternalError("error-123", new IllegalStateException("boom"));

    String rendered = diagnosticsStream.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("[internal-error] error-123"));
    assertTrue(rendered.contains("IllegalStateException: boom"));
  }
}
