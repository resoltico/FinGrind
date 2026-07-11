package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.reportmodel.InventoryValuationReportModelBuilder;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Covers report-writer guardrails that should remain unreachable after argument validation. */
class CliReportResponseWriterCoverageTest extends CliResponseWriterTestSupport {
  @Test
  void writeTrialBalanceResult_rejectsCsvStdoutWhenPdfArtifactPathLeaksPastValidation() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                reportWriter(new ByteArrayOutputStream())
                    .writeTrialBalanceResult(
                        new TrialBalanceResult.Reported(
                            CliFixtureSupport.sampleTrialBalanceReport()),
                        OutputMode.CSV,
                        Path.of("reports/trial-balance.pdf")));

    assertEquals(
        "CSV stdout cannot be combined with --pdf-out after argument validation.",
        exception.getMessage());
  }

  @Test
  void writeInventoryValuationResult_projectsOneModelAcrossJsonTextAndCsv() throws Exception {
    var report = ReportCrossFormatInventoryFixture.sampleInventoryValuationReport(true);
    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    reportWriter(jsonOutput)
        .writeInventoryValuationResult(
            new InventoryValuationResult.Reported(report), OutputMode.JSON, null);
    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    reportWriter(textOutput)
        .writeInventoryValuationResult(
            new InventoryValuationResult.Reported(report), OutputMode.TEXT, null);
    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    reportWriter(csvOutput)
        .writeInventoryValuationResult(
            new InventoryValuationResult.Reported(report), OutputMode.CSV, null);
    ByteArrayOutputStream rejectedOutput = new ByteArrayOutputStream();
    InventoryValuationResult.Rejected rejected =
        new InventoryValuationResult.Rejected(new BookQueryRejection.BookNotInitialized());
    reportWriter(rejectedOutput).writeInventoryValuationResult(rejected, OutputMode.JSON, null);

    assertEquals(
        0,
        CliReportExitCodes.exitCodeFor(
            new InventoryValuationResult.Reported(
                ReportCrossFormatInventoryFixture.sampleInventoryValuationReport(false))));
    assertEquals(2, CliReportExitCodes.exitCodeFor(rejected));
    assertFalse(readJson(jsonOutput).path("payload").has("tabularCsvProjection"));
    assertTrue(textOutput.toString(StandardCharsets.UTF_8).contains("EUR 50.00"));
    assertEquals(
        CsvReportProjector.render(InventoryValuationReportModelBuilder.buildModel(report)) + "\n",
        csvOutput.toString(StandardCharsets.UTF_8));
    assertTrue(csvOutput.toString(StandardCharsets.UTF_8).contains("inventory-reserve"));
    assertTrue(rejectedOutput.toString(StandardCharsets.UTF_8).contains("book-not-initialized"));
  }
}
