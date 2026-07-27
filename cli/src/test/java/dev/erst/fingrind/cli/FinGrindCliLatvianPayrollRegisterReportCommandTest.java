package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Exercises the Latvian payroll-register command through the JSON and PDF report boundary. */
class FinGrindCliLatvianPayrollRegisterReportCommandTest extends CliWorkflowFixtureSupport {
  @Test
  void run_jsonReportWithPdfOut_publishesOneArtifactAndPayrollLifecyclePayload() throws Exception {
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("entity.key");
    Path pdfOutputPath = tempDirectory.resolve("reports").resolve("payroll-register.pdf");
    CliReportPdfArtifactCommandTestSupport.preparePdfOutputParent(pdfOutputPath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();

    int exitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(outputStream),
                utf8PrintStream(diagnosticsStream),
                fixedClock(),
                payrollWorkflow(
                    new LatvianPayrollRegisterResult.Reported(
                        ReportCrossFormatLatvianPayrollFixture
                            .lifecycleLatvianPayrollRegisterReport())))
            .run(
                new String[] {
                  "latvian-payroll-register",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--output",
                  "json",
                  "--pdf-out",
                  pdfOutputPath.toString()
                });

    assertEquals(0, exitCode);
    var envelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("ok", envelope.path("status").stringValue());
    assertEquals("latvian-payroll-register", envelope.path("payload").path("family").stringValue());
    assertEquals(0, envelope.path("payload").path("rows").get(0).path("settlements").size());
    assertEquals(1, envelope.path("artifacts").size());
    assertEquals("pdf", envelope.path("artifacts").get(0).path("format").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(pdfOutputPath.toRealPath()),
        envelope.path("artifacts").get(0).path("path").stringValue());
    assertTrue(Files.exists(pdfOutputPath));
    assertEquals(
        "%PDF-", new String(Files.readAllBytes(pdfOutputPath), 0, 5, StandardCharsets.ISO_8859_1));
    assertEquals("", diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  private static CliBookWorkflow payrollWorkflow(LatvianPayrollRegisterResult result) {
    return new CliBookWorkflowAdapter() {
      @Override
      public ContractDecision<LatvianPayrollRegisterResult> latvianPayrollRegister(
          BookAccess bookAccess, LatvianPayrollRegisterQuery query) {
        return accepted(result);
      }
    };
  }
}
