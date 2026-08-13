package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Publishes operational register report results through the shared CLI output channel. */
@FunctionalInterface
interface CliOperationalReportResultWriter extends CliReportOutputChannelOwner {
  /** Publishes one inventory-valuation result. */
  default void writeInventoryValuationResult(
      InventoryValuationResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.INVENTORY_VALUATION);
  }

  /** Publishes one accrual-cutoff-schedule result. */
  default void writeAccrualCutoffScheduleResult(
      AccrualCutoffScheduleResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.ACCRUAL_CUTOFF_SCHEDULE);
  }

  /** Publishes one fixed-asset-register result. */
  default void writeFixedAssetRegisterResult(
      FixedAssetRegisterResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.FIXED_ASSET_REGISTER);
  }

  /** Publishes one financing-register result. */
  default void writeFinancingRegisterResult(
      FinancingRegisterResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.FINANCING_REGISTER);
  }

  /** Publishes one realized-foreign-exchange-register result. */
  default void writeRealizedForeignExchangeRegisterResult(
      RealizedForeignExchangeRegisterResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.REALIZED_FOREIGN_EXCHANGE_REGISTER);
  }

  /** Publishes one Latvian-payroll-register result. */
  default void writeLatvianPayrollRegisterResult(
      LatvianPayrollRegisterResult result,
      OutputMode outputMode,
      @Nullable PublicationTransactionArtifact exportedArtifact,
      Instant generatedAt) {
    CliReportResultPublishingSupport.write(
        reportOutputChannel(),
        result,
        outputMode,
        exportedArtifact,
        generatedAt,
        CliReportProjections.LATVIAN_PAYROLL_REGISTER);
  }
}
