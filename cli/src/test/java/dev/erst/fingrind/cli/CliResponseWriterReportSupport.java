package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.PrintStream;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Report-side portion of the split test-only response writer compatibility chain. */
class CliResponseWriterReportSupport extends CliResponseWriterBookReadSupport {
  CliResponseWriterReportSupport(PrintStream outputStream) {
    super(outputStream);
  }

  CliResponseWriterReportSupport(PrintStream outputStream, PrintStream diagnosticsStream) {
    super(outputStream, diagnosticsStream);
  }

  void writeAccountBalanceResult(AccountBalanceResult result) {
    writeAccountBalanceResult(result, OutputMode.JSON);
  }

  void writeAccountBalanceResult(AccountBalanceResult result, OutputMode outputMode) {
    reportWriter.writeAccountBalanceResult(result, outputMode, null);
  }

  void writeAccountBalanceResult(
      AccountBalanceResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    reportWriter.writeAccountBalanceResult(result, outputMode, exportedArtifactPath);
  }

  void writeTrialBalanceResult(TrialBalanceResult result, OutputMode outputMode) {
    reportWriter.writeTrialBalanceResult(result, outputMode, null);
  }

  void writeTrialBalanceResult(
      TrialBalanceResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    reportWriter.writeTrialBalanceResult(result, outputMode, exportedArtifactPath);
  }

  void writeAccountLedgerResult(AccountLedgerResult result, OutputMode outputMode) {
    reportWriter.writeAccountLedgerResult(result, outputMode, null);
  }

  void writeAccountLedgerResult(
      AccountLedgerResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    reportWriter.writeAccountLedgerResult(result, outputMode, exportedArtifactPath);
  }

  void writePeriodSummaryResult(PeriodSummaryResult result, OutputMode outputMode) {
    reportWriter.writePeriodSummaryResult(result, outputMode, null);
  }

  void writePeriodSummaryResult(
      PeriodSummaryResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    reportWriter.writePeriodSummaryResult(result, outputMode, exportedArtifactPath);
  }

  void writeFinancialPositionResult(FinancialPositionResult result, OutputMode outputMode) {
    reportWriter.writeFinancialPositionResult(result, outputMode, null);
  }

  void writeFinancialPositionResult(
      FinancialPositionResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    reportWriter.writeFinancialPositionResult(result, outputMode, exportedArtifactPath);
  }

  void writeIncomeStatementResult(IncomeStatementResult result, OutputMode outputMode) {
    reportWriter.writeIncomeStatementResult(result, outputMode, null);
  }

  void writeIncomeStatementResult(
      IncomeStatementResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    reportWriter.writeIncomeStatementResult(result, outputMode, exportedArtifactPath);
  }

  void writeCashFlowStatementResult(CashFlowStatementResult result, OutputMode outputMode) {
    reportWriter.writeCashFlowStatementResult(result, outputMode, null);
  }

  void writeCashFlowStatementResult(
      CashFlowStatementResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    reportWriter.writeCashFlowStatementResult(result, outputMode, exportedArtifactPath);
  }

  void writeChangesInEquityResult(ChangesInEquityResult result, OutputMode outputMode) {
    reportWriter.writeChangesInEquityResult(result, outputMode, null);
  }

  void writeChangesInEquityResult(
      ChangesInEquityResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    reportWriter.writeChangesInEquityResult(result, outputMode, exportedArtifactPath);
  }
}
