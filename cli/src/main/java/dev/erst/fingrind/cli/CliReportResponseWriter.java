package dev.erst.fingrind.cli;

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
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.TaxObligationReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import java.nio.file.Path;
import java.util.Objects;
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
        result.fold(AccountBalanceResult.Reported::snapshot, rejected -> null),
        result.fold(reported -> null, AccountBalanceResult.Rejected::rejection),
        outputMode,
        exportedArtifactPath,
        AccountBalanceReportModelBuilder::buildModel,
        CliAccountBalanceOutputRenderer::renderCsv);
  }

  void writeTrialBalanceResult(
      TrialBalanceResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result.fold(TrialBalanceResult.Reported::report, rejected -> null),
        result.fold(reported -> null, TrialBalanceResult.Rejected::rejection),
        outputMode,
        exportedArtifactPath,
        TrialBalanceReportModelBuilder::buildModel,
        CliReportCsvRenderer::renderTrialBalance);
  }

  void writeAccountLedgerResult(
      AccountLedgerResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result.fold(AccountLedgerResult.Reported::report, rejected -> null),
        result.fold(reported -> null, AccountLedgerResult.Rejected::rejection),
        outputMode,
        exportedArtifactPath,
        AccountLedgerReportModelBuilder::buildModel,
        CliReportCsvRenderer::renderAccountLedger);
  }

  void writePeriodSummaryResult(
      PeriodSummaryResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result.fold(PeriodSummaryResult.Reported::report, rejected -> null),
        result.fold(reported -> null, PeriodSummaryResult.Rejected::rejection),
        outputMode,
        exportedArtifactPath,
        PeriodSummaryReportModelBuilder::buildModel,
        CliReportCsvRenderer::renderPeriodSummary);
  }

  void writeFinancialPositionResult(
      FinancialPositionResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result.fold(FinancialPositionResult.Reported::report, rejected -> null),
        result.fold(reported -> null, FinancialPositionResult.Rejected::rejection),
        outputMode,
        exportedArtifactPath,
        FinancialPositionReportModelBuilder::buildModel,
        CliReportCsvRenderer::renderFinancialPosition);
  }

  void writeIncomeStatementResult(
      IncomeStatementResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result.fold(IncomeStatementResult.Reported::report, rejected -> null),
        result.fold(reported -> null, IncomeStatementResult.Rejected::rejection),
        outputMode,
        exportedArtifactPath,
        IncomeStatementReportModelBuilder::buildModel,
        CliReportCsvRenderer::renderIncomeStatement);
  }

  void writeCashFlowStatementResult(
      CashFlowStatementResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result.fold(CashFlowStatementResult.Reported::report, rejected -> null),
        result.fold(reported -> null, CashFlowStatementResult.Rejected::rejection),
        outputMode,
        exportedArtifactPath,
        CashFlowStatementReportModelBuilder::buildModel,
        CliReportCsvRenderer::renderCashFlowStatement);
  }

  void writeChangesInEquityResult(
      ChangesInEquityResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        result.fold(ChangesInEquityResult.Reported::report, rejected -> null),
        result.fold(reported -> null, ChangesInEquityResult.Rejected::rejection),
        outputMode,
        exportedArtifactPath,
        ChangesInEquityReportModelBuilder::buildModel,
        CliReportCsvRenderer::renderChangesInEquity);
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
      @Nullable REPORTED reported,
      @Nullable BookQueryRejection rejection,
      OutputMode outputMode,
      @Nullable Path exportedArtifactPath,
      Function<REPORTED, ReportModel> reportModelBuilder,
      Function<REPORTED, String> csvRenderer) {
    if (reported == null) {
      writeRejectedResult(Objects.requireNonNull(rejection, "rejection"), outputMode);
      return;
    }
    CliReportPublishingSupport.writeReportedModel(
        outputChannel,
        reportModelBuilder.apply(reported),
        csvRenderer.apply(reported),
        outputMode,
        exportedArtifactPath);
  }

  private void writeRejectedResult(BookQueryRejection rejection, OutputMode outputMode) {
    outputChannel.writeRejectedEnvelope(
        CliRejectionPayloadMapper.queryRejectedEnvelope(rejection), outputMode);
  }
}
