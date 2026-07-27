package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Publishes financial statement report results through the shared CLI output channel. */
@FunctionalInterface
interface CliStatementReportResultWriter extends CliReportOutputChannelOwner {
  /** Publishes one statement-of-financial-position result. */
  default void writeFinancialPositionResult(
      FinancialPositionResult result,
      OutputMode outputMode,
      @Nullable ArtifactPublicationResult exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.FINANCIAL_POSITION);
  }

  /** Publishes one income-statement result. */
  default void writeIncomeStatementResult(
      IncomeStatementResult result,
      OutputMode outputMode,
      @Nullable ArtifactPublicationResult exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.INCOME_STATEMENT);
  }

  /** Publishes one cash-flow-statement result. */
  default void writeCashFlowStatementResult(
      CashFlowStatementResult result,
      OutputMode outputMode,
      @Nullable ArtifactPublicationResult exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.CASH_FLOW_STATEMENT);
  }

  /** Publishes one changes-in-equity result. */
  default void writeChangesInEquityResult(
      ChangesInEquityResult result,
      OutputMode outputMode,
      @Nullable ArtifactPublicationResult exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.CHANGES_IN_EQUITY);
  }
}
