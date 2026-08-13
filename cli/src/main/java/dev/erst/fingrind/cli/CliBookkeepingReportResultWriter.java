package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Publishes core ledger report results through the shared CLI output channel. */
@FunctionalInterface
interface CliBookkeepingReportResultWriter extends CliReportOutputChannelOwner {
  /** Publishes one account-balance result. */
  default void writeAccountBalanceResult(
      AccountBalanceResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.ACCOUNT_BALANCE);
  }

  /** Publishes one trial-balance result. */
  default void writeTrialBalanceResult(
      TrialBalanceResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.TRIAL_BALANCE);
  }

  /** Publishes one account-ledger result. */
  default void writeAccountLedgerResult(
      AccountLedgerResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.ACCOUNT_LEDGER);
  }

  /** Publishes one period-summary result. */
  default void writePeriodSummaryResult(
      PeriodSummaryResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.PERIOD_SUMMARY);
  }
}
