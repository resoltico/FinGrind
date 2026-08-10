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
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.io.PrintStream;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Focused test fixture for report response projections with a deterministic publication time. */
final class CliReportResponseWriterFixture {
  private static final Instant GENERATED_AT = Instant.parse("2026-07-12T01:13:11Z");

  private final CliReportResponseWriter writer;

  CliReportResponseWriterFixture(PrintStream outputStream) {
    writer = new CliReportResponseWriter(CliTestOutputChannels.forOutput(outputStream));
  }

  void writeAccountBalanceResult(AccountBalanceResult result) {
    writeAccountBalanceResult(result, OutputMode.JSON);
  }

  void writeAccountBalanceResult(AccountBalanceResult result, OutputMode outputMode) {
    writeAccountBalanceResult(result, outputMode, null);
  }

  void writeAccountBalanceResult(
      AccountBalanceResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    writer.writeAccountBalanceResult(result, outputMode, exportedArtifact, GENERATED_AT);
  }

  void writeTrialBalanceResult(TrialBalanceResult result, OutputMode outputMode) {
    writeTrialBalanceResult(result, outputMode, null);
  }

  void writeTrialBalanceResult(
      TrialBalanceResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    writer.writeTrialBalanceResult(result, outputMode, exportedArtifact, GENERATED_AT);
  }

  void writeAccountLedgerResult(AccountLedgerResult result, OutputMode outputMode) {
    writeAccountLedgerResult(result, outputMode, null);
  }

  void writeAccountLedgerResult(
      AccountLedgerResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    writer.writeAccountLedgerResult(result, outputMode, exportedArtifact, GENERATED_AT);
  }

  void writePeriodSummaryResult(PeriodSummaryResult result, OutputMode outputMode) {
    writePeriodSummaryResult(result, outputMode, null);
  }

  void writePeriodSummaryResult(
      PeriodSummaryResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    writer.writePeriodSummaryResult(result, outputMode, exportedArtifact, GENERATED_AT);
  }

  void writeFinancialPositionResult(FinancialPositionResult result, OutputMode outputMode) {
    writeFinancialPositionResult(result, outputMode, null);
  }

  void writeFinancialPositionResult(
      FinancialPositionResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    writer.writeFinancialPositionResult(result, outputMode, exportedArtifact, GENERATED_AT);
  }

  void writeIncomeStatementResult(IncomeStatementResult result, OutputMode outputMode) {
    writeIncomeStatementResult(result, outputMode, null);
  }

  void writeIncomeStatementResult(
      IncomeStatementResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    writer.writeIncomeStatementResult(result, outputMode, exportedArtifact, GENERATED_AT);
  }

  void writeCashFlowStatementResult(CashFlowStatementResult result, OutputMode outputMode) {
    writeCashFlowStatementResult(result, outputMode, null);
  }

  void writeCashFlowStatementResult(
      CashFlowStatementResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    writer.writeCashFlowStatementResult(result, outputMode, exportedArtifact, GENERATED_AT);
  }

  void writeChangesInEquityResult(ChangesInEquityResult result, OutputMode outputMode) {
    writeChangesInEquityResult(result, outputMode, null);
  }

  void writeChangesInEquityResult(
      ChangesInEquityResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    writer.writeChangesInEquityResult(result, outputMode, exportedArtifact, GENERATED_AT);
  }

  void writeTaxObligationResult(TaxObligationResult result, OutputMode outputMode) {
    writeTaxObligationResult(result, outputMode, null);
  }

  void writeTaxObligationResult(
      TaxObligationResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact) {
    writer.writeTaxObligationResult(result, outputMode, exportedArtifact, GENERATED_AT);
  }
}
