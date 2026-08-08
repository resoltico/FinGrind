package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared fixtures for PDF-capable report command coverage. */
class CliReportPdfArtifactCommandTestSupport extends CliWorkflowFixtureSupport {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected ExecutedReportCommand executeReportCommand(
      ReportCommandSpec spec,
      Path bookFilePath,
      Path bookKeyFilePath,
      String outputMode,
      Path pdfOutputPath,
      CliBookWorkflow workflow)
      throws IOException {
    return executeReportCommand(
        spec, bookFilePath, bookKeyFilePath, outputMode, pdfOutputPath, workflow, true);
  }

  /** Executes one report command while preserving a deliberately invalid PDF output parent. */
  protected ExecutedReportCommand executeReportCommandWithoutPreparingPdfOutputParent(
      ReportCommandSpec spec,
      Path bookFilePath,
      Path bookKeyFilePath,
      String outputMode,
      Path pdfOutputPath,
      CliBookWorkflow workflow)
      throws IOException {
    return executeReportCommand(
        spec, bookFilePath, bookKeyFilePath, outputMode, pdfOutputPath, workflow, false);
  }

  private ExecutedReportCommand executeReportCommand(
      ReportCommandSpec spec,
      Path bookFilePath,
      Path bookKeyFilePath,
      String outputMode,
      Path pdfOutputPath,
      CliBookWorkflow workflow,
      boolean prepareOutputParent)
      throws IOException {
    if (prepareOutputParent) {
      preparePdfOutputParent(pdfOutputPath);
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    int exitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(outputStream),
                utf8PrintStream(diagnosticsStream),
                fixedClock(),
                workflow)
            .run(spec.arguments(bookFilePath, bookKeyFilePath, outputMode, pdfOutputPath));
    return new ExecutedReportCommand(
        exitCode,
        outputStream.toString(StandardCharsets.UTF_8),
        diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  protected static JsonNode readJson(String jsonText) throws IOException {
    return OBJECT_MAPPER.readTree(jsonText);
  }

  protected static void assertPdfSignature(Path pdfOutputPath) throws IOException {
    assertEquals(
        "%PDF-", new String(Files.readAllBytes(pdfOutputPath), 0, 5, StandardCharsets.ISO_8859_1));
  }

  static void preparePdfOutputParent(Path pdfOutputPath) throws IOException {
    Path parentDirectory = CliPdfReportExporter.parentDirectory(pdfOutputPath.toAbsolutePath());
    if (Files.notExists(parentDirectory)) {
      Files.createDirectories(parentDirectory);
    }
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentDirectory);
  }

  protected static List<ReportCommandSpec> pdfCapableReportCommandSpecs() {
    return List.of(
        new ReportCommandSpec(
            OperationId.TAX_OBLIGATION,
            List.of(
                "--tax-registration-id",
                "vat-lv",
                "--period-start",
                "2026-04-01",
                "--period-end",
                "2026-04-30"),
            successfulTaxObligationWorkflow(),
            rejectedTaxObligationWorkflow()),
        new ReportCommandSpec(
            OperationId.ACCOUNT_BALANCE,
            List.of("--account-code", "1000"),
            successfulAccountBalanceWorkflow(),
            rejectedAccountBalanceWorkflow()),
        new ReportCommandSpec(
            OperationId.TRIAL_BALANCE,
            List.of(),
            successfulTrialBalanceWorkflow(),
            rejectedTrialBalanceWorkflow()),
        new ReportCommandSpec(
            OperationId.ACCOUNT_LEDGER,
            List.of(
                "--account-code",
                "1000",
                "--effective-date-from",
                "2026-04-01",
                "--effective-date-to",
                "2026-04-30"),
            successfulAccountLedgerWorkflow(),
            rejectedAccountLedgerWorkflow()),
        new ReportCommandSpec(
            OperationId.PERIOD_SUMMARY,
            List.of("--period-start", "2026-04-01", "--period-end", "2026-04-30"),
            successfulPeriodSummaryWorkflow(),
            rejectedPeriodSummaryWorkflow()),
        new ReportCommandSpec(
            OperationId.FINANCIAL_POSITION,
            List.of("--effective-date-as-of", "2026-04-30"),
            successfulFinancialPositionWorkflow(),
            rejectedFinancialPositionWorkflow()),
        new ReportCommandSpec(
            OperationId.INVENTORY_VALUATION,
            List.of("--as-of", "2026-04-30", "--movements"),
            CliPdfOperationalReportWorkflowFixtures.inventoryValuation(true),
            CliPdfOperationalReportWorkflowFixtures.inventoryValuation(false)),
        new ReportCommandSpec(
            OperationId.ACCRUAL_CUTOFF_SCHEDULE,
            List.of("--as-of", "2026-04-30"),
            CliPdfOperationalReportWorkflowFixtures.accrualCutoffSchedule(true),
            CliPdfOperationalReportWorkflowFixtures.accrualCutoffSchedule(false)),
        new ReportCommandSpec(
            OperationId.FIXED_ASSET_REGISTER,
            List.of("--as-of", "2026-04-30"),
            CliPdfOperationalReportWorkflowFixtures.fixedAssetRegister(true),
            CliPdfOperationalReportWorkflowFixtures.fixedAssetRegister(false)),
        new ReportCommandSpec(
            OperationId.FINANCING_REGISTER,
            List.of(),
            CliPdfOperationalReportWorkflowFixtures.financingRegister(true),
            CliPdfOperationalReportWorkflowFixtures.financingRegister(false)),
        new ReportCommandSpec(
            OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER,
            List.of(),
            CliPdfOperationalReportWorkflowFixtures.realizedForeignExchangeRegister(true),
            CliPdfOperationalReportWorkflowFixtures.realizedForeignExchangeRegister(false)),
        new ReportCommandSpec(
            OperationId.LATVIAN_PAYROLL_REGISTER,
            List.of(),
            CliPdfOperationalReportWorkflowFixtures.latvianPayrollRegister(true),
            CliPdfOperationalReportWorkflowFixtures.latvianPayrollRegister(false)),
        new ReportCommandSpec(
            OperationId.INCOME_STATEMENT,
            List.of("--period-start", "2026-04-01", "--period-end", "2026-04-30"),
            successfulIncomeStatementWorkflow(),
            rejectedIncomeStatementWorkflow()),
        new ReportCommandSpec(
            OperationId.CASH_FLOW_STATEMENT,
            List.of("--period-start", "2026-04-01", "--period-end", "2026-04-30"),
            successfulCashFlowStatementWorkflow(),
            rejectedCashFlowStatementWorkflow()),
        new ReportCommandSpec(
            OperationId.CHANGES_IN_EQUITY,
            List.of("--period-start", "2026-04-01", "--period-end", "2026-04-30"),
            successfulChangesInEquityWorkflow(),
            rejectedChangesInEquityWorkflow()));
  }

  private static CliBookWorkflow successfulAccountBalanceWorkflow() {
    return reportingWorkflow(
        new AccountBalanceResult.Reported(sampleAccountBalanceSnapshot()),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow rejectedAccountBalanceWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow successfulTrialBalanceWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        new TrialBalanceResult.Reported(sampleTrialBalanceReport()),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow rejectedTrialBalanceWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow successfulAccountLedgerWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        new AccountLedgerResult.Reported(sampleAccountLedgerReport()),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow rejectedAccountLedgerWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow successfulPeriodSummaryWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        new PeriodSummaryResult.Reported(samplePeriodSummaryReport()),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow rejectedPeriodSummaryWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow successfulFinancialPositionWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        new FinancialPositionResult.Reported(sampleFinancialPositionReport()),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow rejectedFinancialPositionWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow successfulIncomeStatementWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        new IncomeStatementResult.Reported(sampleIncomeStatementReport()),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow rejectedIncomeStatementWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow successfulCashFlowStatementWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        new CashFlowStatementResult.Reported(sampleCashFlowStatementReport()),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow rejectedCashFlowStatementWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedCashFlowStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow successfulChangesInEquityWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        new ChangesInEquityResult.Reported(sampleChangesInEquityReport()));
  }

  private static CliBookWorkflow rejectedChangesInEquityWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow successfulTaxObligationWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        new TaxObligationResult.Reported(ReportCrossFormatTaxFixture.sampleTaxObligationReport()),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static CliBookWorkflow rejectedTaxObligationWorkflow() {
    return reportingWorkflow(
        rejectedAccountBalanceResult(),
        rejectedTaxObligationResult(),
        rejectedTrialBalanceResult(),
        rejectedAccountLedgerResult(),
        rejectedPeriodSummaryResult(),
        rejectedFinancialPositionResult(),
        rejectedIncomeStatementResult(),
        rejectedChangesInEquityResult());
  }

  private static AccountBalanceResult rejectedAccountBalanceResult() {
    return new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static TrialBalanceResult rejectedTrialBalanceResult() {
    return new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static AccountLedgerResult rejectedAccountLedgerResult() {
    return new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static PeriodSummaryResult rejectedPeriodSummaryResult() {
    return new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static FinancialPositionResult rejectedFinancialPositionResult() {
    return new FinancialPositionResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static IncomeStatementResult rejectedIncomeStatementResult() {
    return new IncomeStatementResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static CashFlowStatementResult rejectedCashFlowStatementResult() {
    return new CashFlowStatementResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static ChangesInEquityResult rejectedChangesInEquityResult() {
    return new ChangesInEquityResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  protected record ReportCommandSpec(
      OperationId operationId,
      List<String> requiredArguments,
      CliBookWorkflow successfulWorkflow,
      CliBookWorkflow rejectedWorkflow) {
    String commandName() {
      return operationId.wireName();
    }

    String[] arguments(
        Path bookFilePath, Path bookKeyFilePath, String outputMode, Path pdfOutputPath) {
      List<String> arguments = new ArrayList<>();
      arguments.add(commandName());
      arguments.add("--book-file");
      arguments.add(bookFilePath.toString());
      arguments.add("--book-key-file");
      arguments.add(bookKeyFilePath.toString());
      arguments.addAll(requiredArguments);
      arguments.add("--output");
      arguments.add(outputMode);
      arguments.add("--pdf-out");
      arguments.add(pdfOutputPath.toString());
      return arguments.toArray(String[]::new);
    }
  }

  protected record ExecutedReportCommand(int exitCode, String outputText, String diagnosticsText) {}
}
