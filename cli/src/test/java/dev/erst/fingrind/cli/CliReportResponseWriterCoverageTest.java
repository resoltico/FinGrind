package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Covers report-writer guardrails that should remain unreachable after argument validation. */
class CliReportResponseWriterCoverageTest extends CliResponseWriterTestSupport {
  private static final Instant GENERATED_AT = Instant.parse("2026-07-12T01:13:11Z");

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
                        Path.of("reports/trial-balance.pdf"),
                        GENERATED_AT));

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
            new InventoryValuationResult.Reported(report), OutputMode.JSON, null, GENERATED_AT);
    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    reportWriter(textOutput)
        .writeInventoryValuationResult(
            new InventoryValuationResult.Reported(report), OutputMode.TEXT, null, GENERATED_AT);
    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    reportWriter(csvOutput)
        .writeInventoryValuationResult(
            new InventoryValuationResult.Reported(report), OutputMode.CSV, null, GENERATED_AT);
    ByteArrayOutputStream rejectedOutput = new ByteArrayOutputStream();
    InventoryValuationResult.Rejected rejected =
        new InventoryValuationResult.Rejected(new BookQueryRejection.BookNotInitialized());
    reportWriter(rejectedOutput)
        .writeInventoryValuationResult(rejected, OutputMode.JSON, null, GENERATED_AT);

    assertEquals(
        0,
        CliReportExitCodes.exitCodeFor(
            new InventoryValuationResult.Reported(
                ReportCrossFormatInventoryFixture.sampleInventoryValuationReport(false))));
    assertEquals(2, CliReportExitCodes.exitCodeFor(rejected));
    assertFalse(readJson(jsonOutput).path("payload").has("tabularCsvProjection"));
    assertTrue(textOutput.toString(StandardCharsets.UTF_8).contains("EUR 50.00"));
    assertEquals(
        CliSemanticReportCsvRenderer.render(
                CliReportPayloadMapper.inventoryValuation(report, GENERATED_AT))
            + "\n",
        csvOutput.toString(StandardCharsets.UTF_8));
    assertTrue(csvOutput.toString(StandardCharsets.UTF_8).contains("inventory-reserve"));
    assertTrue(rejectedOutput.toString(StandardCharsets.UTF_8).contains("book-not-initialized"));
  }

  @Test
  void writeAccrualCutoffScheduleResult_projectsOneModelAcrossJsonTextAndCsv() throws Exception {
    var report = ReportCrossFormatAccrualCutoffFixture.sampleAccrualCutoffScheduleReport();
    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    reportWriter(jsonOutput)
        .writeAccrualCutoffScheduleResult(
            new AccrualCutoffScheduleResult.Reported(report), OutputMode.JSON, null, GENERATED_AT);
    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    reportWriter(textOutput)
        .writeAccrualCutoffScheduleResult(
            new AccrualCutoffScheduleResult.Reported(report), OutputMode.TEXT, null, GENERATED_AT);
    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    reportWriter(csvOutput)
        .writeAccrualCutoffScheduleResult(
            new AccrualCutoffScheduleResult.Reported(report), OutputMode.CSV, null, GENERATED_AT);
    ByteArrayOutputStream rejectedOutput = new ByteArrayOutputStream();
    AccrualCutoffScheduleResult.Rejected rejected =
        new AccrualCutoffScheduleResult.Rejected(new BookQueryRejection.BookNotInitialized());
    reportWriter(rejectedOutput)
        .writeAccrualCutoffScheduleResult(rejected, OutputMode.JSON, null, GENERATED_AT);

    assertEquals(
        0, CliReportExitCodes.exitCodeFor(new AccrualCutoffScheduleResult.Reported(report)));
    assertEquals(2, CliReportExitCodes.exitCodeFor(rejected));
    assertFalse(readJson(jsonOutput).path("payload").has("tabularCsvProjection"));
    assertTrue(textOutput.toString(StandardCharsets.UTF_8).contains("EUR 90.00"));
    assertEquals(
        CliSemanticReportCsvRenderer.render(
                CliReportPayloadMapper.accrualCutoffSchedule(report, GENERATED_AT))
            + "\n",
        csvOutput.toString(StandardCharsets.UTF_8));
    assertTrue(csvOutput.toString(StandardCharsets.UTF_8).contains("prepayment-2026"));
    assertTrue(rejectedOutput.toString(StandardCharsets.UTF_8).contains("book-not-initialized"));
  }

  @Test
  void writeLatvianPayrollRegisterResult_projectsOneModelAcrossJsonTextAndCsv() throws Exception {
    var report = ReportCrossFormatLatvianPayrollFixture.sampleLatvianPayrollRegisterReport();
    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    reportWriter(jsonOutput)
        .writeLatvianPayrollRegisterResult(
            new LatvianPayrollRegisterResult.Reported(report), OutputMode.JSON, null, GENERATED_AT);
    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    reportWriter(textOutput)
        .writeLatvianPayrollRegisterResult(
            new LatvianPayrollRegisterResult.Reported(report), OutputMode.TEXT, null, GENERATED_AT);
    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    reportWriter(csvOutput)
        .writeLatvianPayrollRegisterResult(
            new LatvianPayrollRegisterResult.Reported(report), OutputMode.CSV, null, GENERATED_AT);
    ByteArrayOutputStream rejectedOutput = new ByteArrayOutputStream();
    LatvianPayrollRegisterResult.Rejected rejected =
        new LatvianPayrollRegisterResult.Rejected(new BookQueryRejection.BookNotInitialized());
    reportWriter(rejectedOutput)
        .writeLatvianPayrollRegisterResult(rejected, OutputMode.JSON, null, GENERATED_AT);

    assertEquals(
        0, CliReportExitCodes.exitCodeFor(new LatvianPayrollRegisterResult.Reported(report)));
    assertEquals(2, CliReportExitCodes.exitCodeFor(rejected));
    assertFalse(readJson(jsonOutput).path("payload").has("tabularCsvProjection"));
    assertEquals(0, readJson(jsonOutput).path("payload").path("resolvedQuery").properties().size());
    assertTrue(textOutput.toString(StandardCharsets.UTF_8).contains("state-remittance"));
    assertEquals(
        CliSemanticReportCsvRenderer.render(
                CliReportPayloadMapper.latvianPayrollRegister(report, GENERATED_AT))
            + "\n",
        csvOutput.toString(StandardCharsets.UTF_8));
    assertFalse(csvOutput.toString(StandardCharsets.UTF_8).contains("unsettled"));
    assertTrue(rejectedOutput.toString(StandardCharsets.UTF_8).contains("book-not-initialized"));
  }

  @Test
  void writeFixedAssetRegisterResult_projectsOneModelAcrossJsonTextAndCsv() throws Exception {
    var report = ReportCrossFormatLifecycleContextFixture.fixedAssetRegisterReport();
    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    reportWriter(jsonOutput)
        .writeFixedAssetRegisterResult(
            new FixedAssetRegisterResult.Reported(report), OutputMode.JSON, null, GENERATED_AT);
    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    reportWriter(textOutput)
        .writeFixedAssetRegisterResult(
            new FixedAssetRegisterResult.Reported(report), OutputMode.TEXT, null, GENERATED_AT);
    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    reportWriter(csvOutput)
        .writeFixedAssetRegisterResult(
            new FixedAssetRegisterResult.Reported(report), OutputMode.CSV, null, GENERATED_AT);

    assertEquals(0, CliReportExitCodes.exitCodeFor(new FixedAssetRegisterResult.Reported(report)));
    assertFalse(readJson(jsonOutput).path("payload").has("tabularCsvProjection"));
    assertTrue(textOutput.toString(StandardCharsets.UTF_8).contains("asset-vehicle-001"));
    assertEquals(
        CliSemanticReportCsvRenderer.render(
                CliReportPayloadMapper.fixedAssetRegister(report, GENERATED_AT))
            + "\n",
        csvOutput.toString(StandardCharsets.UTF_8));
    assertTrue(csvOutput.toString(StandardCharsets.UTF_8).contains("asset-vehicle-001"));
  }

  @Test
  void writeFinancingRegisterResult_projectsOneModelAcrossJsonTextAndCsv() throws Exception {
    var report = ReportCrossFormatLifecycleContextFixture.financingRegisterReport();
    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    reportWriter(jsonOutput)
        .writeFinancingRegisterResult(
            new FinancingRegisterResult.Reported(report), OutputMode.JSON, null, GENERATED_AT);
    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    reportWriter(textOutput)
        .writeFinancingRegisterResult(
            new FinancingRegisterResult.Reported(report), OutputMode.TEXT, null, GENERATED_AT);
    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    reportWriter(csvOutput)
        .writeFinancingRegisterResult(
            new FinancingRegisterResult.Reported(report), OutputMode.CSV, null, GENERATED_AT);

    assertEquals(0, CliReportExitCodes.exitCodeFor(new FinancingRegisterResult.Reported(report)));
    assertFalse(readJson(jsonOutput).path("payload").has("tabularCsvProjection"));
    assertTrue(textOutput.toString(StandardCharsets.UTF_8).contains("loan-working-capital-001"));
    assertEquals(
        CliSemanticReportCsvRenderer.render(
                CliReportPayloadMapper.financingRegister(report, GENERATED_AT))
            + "\n",
        csvOutput.toString(StandardCharsets.UTF_8));
    assertTrue(csvOutput.toString(StandardCharsets.UTF_8).contains("loan-working-capital-001"));
  }

  @Test
  void writeRealizedForeignExchangeRegisterResult_projectsOneModelAcrossJsonTextAndCsv()
      throws Exception {
    var report = ReportCrossFormatLifecycleContextFixture.realizedForeignExchangeRegisterReport();
    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    reportWriter(jsonOutput)
        .writeRealizedForeignExchangeRegisterResult(
            new RealizedForeignExchangeRegisterResult.Reported(report),
            OutputMode.JSON,
            null,
            GENERATED_AT);
    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    reportWriter(textOutput)
        .writeRealizedForeignExchangeRegisterResult(
            new RealizedForeignExchangeRegisterResult.Reported(report),
            OutputMode.TEXT,
            null,
            GENERATED_AT);
    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    reportWriter(csvOutput)
        .writeRealizedForeignExchangeRegisterResult(
            new RealizedForeignExchangeRegisterResult.Reported(report),
            OutputMode.CSV,
            null,
            GENERATED_AT);

    assertEquals(
        0,
        CliReportExitCodes.exitCodeFor(new RealizedForeignExchangeRegisterResult.Reported(report)));
    assertFalse(readJson(jsonOutput).path("payload").has("tabularCsvProjection"));
    assertTrue(textOutput.toString(StandardCharsets.UTF_8).contains("receivable-usd-001"));
    assertEquals(
        CliSemanticReportCsvRenderer.render(
                CliReportPayloadMapper.realizedForeignExchangeRegister(report, GENERATED_AT))
            + "\n",
        csvOutput.toString(StandardCharsets.UTF_8));
    assertTrue(csvOutput.toString(StandardCharsets.UTF_8).contains("receivable-usd-001"));
  }
}
