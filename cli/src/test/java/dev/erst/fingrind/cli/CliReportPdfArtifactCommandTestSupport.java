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
class CliReportPdfArtifactCommandTestSupport extends FinGrindCliTestSupport {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected ExecutedReportCommand executeReportCommand(
      ReportCommandSpec spec,
      Path bookFilePath,
      Path bookKeyFilePath,
      String outputMode,
      Path pdfOutputPath,
      CliBookWorkflow workflow) {
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

  protected static List<ReportCommandSpec> pdfCapableReportCommandSpecs() {
    return List.of(
        new ReportCommandSpec(
            "account-balance",
            List.of("--account-code", "1000"),
            successfulAccountBalanceWorkflow(),
            rejectedAccountBalanceWorkflow()),
        new ReportCommandSpec(
            "trial-balance",
            List.of(),
            successfulTrialBalanceWorkflow(),
            rejectedTrialBalanceWorkflow()),
        new ReportCommandSpec(
            "account-ledger",
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
            "period-summary",
            List.of("--period-start", "2026-04-01", "--period-end", "2026-04-30"),
            successfulPeriodSummaryWorkflow(),
            rejectedPeriodSummaryWorkflow()),
        new ReportCommandSpec(
            "financial-position",
            List.of("--effective-date-as-of", "2026-04-30"),
            successfulFinancialPositionWorkflow(),
            rejectedFinancialPositionWorkflow()),
        new ReportCommandSpec(
            "income-statement",
            List.of("--period-start", "2026-04-01", "--period-end", "2026-04-30"),
            successfulIncomeStatementWorkflow(),
            rejectedIncomeStatementWorkflow()),
        new ReportCommandSpec(
            "cash-flow-statement",
            List.of("--period-start", "2026-04-01", "--period-end", "2026-04-30"),
            successfulCashFlowStatementWorkflow(),
            rejectedCashFlowStatementWorkflow()),
        new ReportCommandSpec(
            "changes-in-equity",
            List.of("--period-start", "2026-04-01", "--period-end", "2026-04-30"),
            successfulChangesInEquityWorkflow(),
            rejectedChangesInEquityWorkflow()));
  }

  private static CliBookWorkflow successfulAccountBalanceWorkflow() {
    return reportingWorkflow(
        new AccountBalanceResult.Reported(sampleAccountBalanceSnapshot()),
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
      String commandName,
      List<String> requiredArguments,
      CliBookWorkflow successfulWorkflow,
      CliBookWorkflow rejectedWorkflow) {
    String[] arguments(
        Path bookFilePath, Path bookKeyFilePath, String outputMode, Path pdfOutputPath) {
      List<String> arguments = new ArrayList<>();
      arguments.add(commandName);
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
