package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
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
        CliReportResultAccess.accountBalanceSnapshot(result),
        CliReportResultAccess.accountBalanceRejection(result),
        outputMode,
        exportedArtifactPath,
        CliBookQueryPayloadMapper::accountBalancePayload,
        CliAccountBalanceOutputRenderer::renderText,
        CliAccountBalanceOutputRenderer::renderCsv);
  }

  void writeTrialBalanceResult(
      TrialBalanceResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        CliReportResultAccess.trialBalanceReport(result),
        CliReportResultAccess.trialBalanceRejection(result),
        outputMode,
        exportedArtifactPath,
        CliReportPayloadMapper::trialBalancePayload,
        CliReportOutputRenderer::renderTrialBalanceText,
        CliReportOutputRenderer::renderTrialBalanceCsv);
  }

  void writeAccountLedgerResult(
      AccountLedgerResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        CliReportResultAccess.accountLedgerReport(result),
        CliReportResultAccess.accountLedgerRejection(result),
        outputMode,
        exportedArtifactPath,
        CliReportPayloadMapper::accountLedgerPayload,
        CliReportOutputRenderer::renderAccountLedgerText,
        CliReportOutputRenderer::renderAccountLedgerCsv);
  }

  void writePeriodSummaryResult(
      PeriodSummaryResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        CliReportResultAccess.periodSummaryReport(result),
        CliReportResultAccess.periodSummaryRejection(result),
        outputMode,
        exportedArtifactPath,
        CliReportPayloadMapper::periodSummaryPayload,
        CliReportOutputRenderer::renderPeriodSummaryText,
        CliReportOutputRenderer::renderPeriodSummaryCsv);
  }

  void writeFinancialPositionResult(
      FinancialPositionResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        CliReportResultAccess.financialPositionReport(result),
        CliReportResultAccess.financialPositionRejection(result),
        outputMode,
        exportedArtifactPath,
        CliReportPayloadMapper::financialPositionPayload,
        CliReportOutputRenderer::renderFinancialPositionText,
        CliReportOutputRenderer::renderFinancialPositionCsv);
  }

  void writeIncomeStatementResult(
      IncomeStatementResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        CliReportResultAccess.incomeStatementReport(result),
        CliReportResultAccess.incomeStatementRejection(result),
        outputMode,
        exportedArtifactPath,
        CliReportPayloadMapper::incomeStatementPayload,
        CliReportOutputRenderer::renderIncomeStatementText,
        CliReportOutputRenderer::renderIncomeStatementCsv);
  }

  void writeChangesInEquityResult(
      ChangesInEquityResult result, OutputMode outputMode, @Nullable Path exportedArtifactPath) {
    writeResult(
        CliReportResultAccess.changesInEquityReport(result),
        CliReportResultAccess.changesInEquityRejection(result),
        outputMode,
        exportedArtifactPath,
        CliReportPayloadMapper::changesInEquityPayload,
        CliReportOutputRenderer::renderChangesInEquityText,
        CliReportOutputRenderer::renderChangesInEquityCsv);
  }

  private <REPORTED> void writeResult(
      @Nullable REPORTED reported,
      @Nullable BookQueryRejection rejection,
      OutputMode outputMode,
      @Nullable Path exportedArtifactPath,
      Function<REPORTED, ? extends ProtocolSuccessPayload> payloadMapper,
      Function<REPORTED, String> textRenderer,
      Function<REPORTED, String> csvRenderer) {
    if (reported == null) {
      writeRejectedResult(Objects.requireNonNull(rejection, "rejection"));
      return;
    }
    writeReportedResult(
        outputMode,
        exportedArtifactPath,
        () -> payloadMapper.apply(reported),
        () -> textRenderer.apply(reported),
        () -> csvRenderer.apply(reported));
  }

  private void writeReportedResult(
      OutputMode outputMode,
      @Nullable Path exportedArtifactPath,
      Supplier<? extends ProtocolSuccessPayload> payloadSupplier,
      Supplier<String> textSupplier,
      Supplier<String> csvSupplier) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliEnvelopeMapper.successEnvelope(payloadSupplier.get(), exportedArtifactPath)),
        () -> outputChannel.writeText(textSupplier.get()),
        () -> outputChannel.writeText(csvSupplier.get()));
  }

  private void writeRejectedResult(BookQueryRejection rejection) {
    outputChannel.writeQueryRejection(CliRejectionPayloadMapper.queryRejectedEnvelope(rejection));
  }
}
