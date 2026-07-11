package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryReportResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.InventoryValuationReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.TaxObligationReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Renders report-producing read-side CLI results through the shared output channel. */
final class CliReportResponseWriter {
  private final CliOutputChannel outputChannel;

  CliReportResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeAccountBalanceResult(
      AccountBalanceResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result,
        outputMode,
        exportedArtifactPath,
        AccountBalanceReportModelBuilder::buildModel,
        (reported, ignored) -> CliAccountBalanceOutputRenderer.renderCsv(reported));
  }

  void writeTrialBalanceResult(
      TrialBalanceResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result,
        outputMode,
        exportedArtifactPath,
        TrialBalanceReportModelBuilder::buildModel,
        (reported, ignored) -> CliReportCsvRenderer.renderTrialBalance(reported));
  }

  void writeAccountLedgerResult(
      AccountLedgerResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result,
        outputMode,
        exportedArtifactPath,
        AccountLedgerReportModelBuilder::buildModel,
        (reported, ignored) -> CliReportCsvRenderer.renderAccountLedger(reported));
  }

  void writePeriodSummaryResult(
      PeriodSummaryResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result,
        outputMode,
        exportedArtifactPath,
        PeriodSummaryReportModelBuilder::buildModel,
        (reported, ignored) -> CliReportCsvRenderer.renderPeriodSummary(reported));
  }

  void writeFinancialPositionResult(
      FinancialPositionResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result,
        outputMode,
        exportedArtifactPath,
        FinancialPositionReportModelBuilder::buildModel,
        (reported, ignored) -> CliReportCsvRenderer.renderFinancialPosition(reported));
  }

  void writeIncomeStatementResult(
      IncomeStatementResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result,
        outputMode,
        exportedArtifactPath,
        IncomeStatementReportModelBuilder::buildModel,
        (reported, ignored) -> CliReportCsvRenderer.renderIncomeStatement(reported));
  }

  void writeInventoryValuationResult(
      InventoryValuationResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result,
        outputMode,
        exportedArtifactPath,
        InventoryValuationReportModelBuilder::buildModel,
        (ignored, reportModel) -> CsvReportProjector.render(reportModel));
  }

  void writeCashFlowStatementResult(
      CashFlowStatementResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result,
        outputMode,
        exportedArtifactPath,
        CashFlowStatementReportModelBuilder::buildModel,
        (reported, ignored) -> CliReportCsvRenderer.renderCashFlowStatement(reported));
  }

  void writeChangesInEquityResult(
      ChangesInEquityResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result,
        outputMode,
        exportedArtifactPath,
        ChangesInEquityReportModelBuilder::buildModel,
        (reported, ignored) -> CliReportCsvRenderer.renderChangesInEquity(reported));
  }

  void writeTaxObligationResult(
      TaxObligationResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    switch (result) {
      case TaxObligationResult.Reported reported ->
          CliReportPublishingSupport.writeReportedModel(
              outputChannel,
              TaxObligationReportModelBuilder.buildModel(reported.report()),
              CliReportCsvRenderer.renderTaxObligation(reported.report()),
              outputMode,
              exportedArtifactPath);
      case TaxObligationResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.taxQueryRejectedEnvelope(
                  OperationId.TAX_OBLIGATION, rejected.rejection()),
              outputMode);
    }
  }

  private <REPORTED> void writeResult(
      BookQueryReportResult<REPORTED> result,
      OutputMode outputMode,
      @Nullable Path exportedArtifactPath,
      Function<REPORTED, ReportModel> reportModelBuilder,
      BiFunction<REPORTED, ReportModel, String> csvRenderer) {
    REPORTED reported = result.reported();
    if (reported == null) {
      writeRejectedResult(Objects.requireNonNull(result.rejection(), "rejection"), outputMode);
      return;
    }
    ReportModel reportModel = reportModelBuilder.apply(reported);
    CliReportPublishingSupport.writeReportedModel(
        outputChannel,
        reportModel,
        csvRenderer.apply(reported, reportModel),
        outputMode,
        exportedArtifactPath);
  }

  private void writeRejectedResult(BookQueryRejection rejection, OutputMode outputMode) {
    outputChannel.writeRejectedEnvelope(
        CliRejectionPayloadMapper.queryRejectedEnvelope(rejection), outputMode);
  }
}
