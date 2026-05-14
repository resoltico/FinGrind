package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import java.nio.file.Path;
import java.util.Objects;

/** Renders query and reporting CLI results through the shared output channel. */
final class CliQueryResponseWriter {
  private final CliOutputChannel outputChannel;

  CliQueryResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeBookInspection(Path bookFilePath, BookInspection inspection, OutputMode outputMode) {
    outputMode.run(
        () ->
            outputChannel.writeEnvelope(
                CliResponsePayloadMapper.successEnvelope(
                    CliResponsePayloadMapper.bookInspectionPayload(bookFilePath, inspection))),
        () ->
            outputChannel.writeText(
                CliQueryOutputRenderer.renderBookInspectionHuman(bookFilePath, inspection)),
        () -> {
          throw new IllegalArgumentException(
              CliOperationText.unsupportedCsvOutput(OperationId.INSPECT_BOOK));
        });
  }

  void writeListAccountsResult(ListAccountsResult result, OutputMode outputMode) {
    switch (result) {
      case ListAccountsResult.Listed listed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.accountPagePayload(listed.page()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderAccountsHuman(listed.page())),
              () ->
                  outputChannel.writeText(CliQueryOutputRenderer.renderAccountsCsv(listed.page())));
      case ListAccountsResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
    switch (result) {
      case GetPostingResult.Found found ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.postingPayload(found.postingFact()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderPostingHuman(found.postingFact())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.GET_POSTING));
              });
      case GetPostingResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeListPostingsResult(ListPostingsResult result, OutputMode outputMode) {
    switch (result) {
      case ListPostingsResult.Listed listed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.postingPagePayload(listed.page()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderPostingRegisterHuman(listed.page())),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderPostingRegisterCsv(listed.page())));
      case ListPostingsResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeAccountBalanceResult(AccountBalanceResult result, OutputMode outputMode) {
    switch (result) {
      case AccountBalanceResult.Reported reported ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.accountBalancePayload(reported.snapshot()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderAccountBalanceHuman(reported.snapshot())),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderAccountBalanceCsv(reported.snapshot())));
      case AccountBalanceResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeTrialBalanceResult(TrialBalanceResult result, OutputMode outputMode) {
    switch (result) {
      case TrialBalanceResult.Reported reported ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.trialBalancePayload(reported.report()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderTrialBalanceHuman(reported.report())),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderTrialBalanceCsv(reported.report())));
      case TrialBalanceResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeAccountLedgerResult(AccountLedgerResult result, OutputMode outputMode) {
    switch (result) {
      case AccountLedgerResult.Reported reported ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.accountLedgerPayload(reported.report()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderAccountLedgerHuman(reported.report())),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderAccountLedgerCsv(reported.report())));
      case AccountLedgerResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writePeriodSummaryResult(PeriodSummaryResult result, OutputMode outputMode) {
    switch (result) {
      case PeriodSummaryResult.Reported reported ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.periodSummaryPayload(reported.report()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderPeriodSummaryHuman(reported.report())),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderPeriodSummaryCsv(reported.report())));
      case PeriodSummaryResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeFinancialPositionResult(FinancialPositionResult result, OutputMode outputMode) {
    switch (result) {
      case FinancialPositionResult.Reported reported ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.financialPositionPayload(reported.report()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderFinancialPositionHuman(reported.report())),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderFinancialPositionCsv(reported.report())));
      case FinancialPositionResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeIncomeStatementResult(IncomeStatementResult result, OutputMode outputMode) {
    switch (result) {
      case IncomeStatementResult.Reported reported ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.incomeStatementPayload(reported.report()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderIncomeStatementHuman(reported.report())),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderIncomeStatementCsv(reported.report())));
      case IncomeStatementResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeChangesInEquityResult(ChangesInEquityResult result, OutputMode outputMode) {
    switch (result) {
      case ChangesInEquityResult.Reported reported ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliResponsePayloadMapper.successEnvelope(
                          CliResponsePayloadMapper.changesInEquityPayload(reported.report()))),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderChangesInEquityHuman(reported.report())),
              () ->
                  outputChannel.writeText(
                      CliQueryOutputRenderer.renderChangesInEquityCsv(reported.report())));
      case ChangesInEquityResult.Rejected rejected ->
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
    }
  }

  void writeLedgerPlanResult(LedgerPlanResult result) {
    Record envelope =
        switch (result) {
          case LedgerPlanResult.Succeeded succeeded ->
              CliResponsePayloadMapper.successEnvelope(
                  CliResponsePayloadMapper.ledgerPlanPayload(succeeded));
          case LedgerPlanResult.Rejected rejected ->
              CliResponsePayloadMapper.rejectedPlanEnvelope(
                  rejected, ProtocolRejectionStatus.PLAN_REJECTED);
          case LedgerPlanResult.AssertionFailed assertionFailed ->
              CliResponsePayloadMapper.rejectedPlanEnvelope(
                  assertionFailed, ProtocolRejectionStatus.PLAN_ASSERTION_FAILED);
        };
    outputChannel.writeEnvelope(envelope);
  }

  static ProtocolRejectionStatus planRejectionStatus(LedgerPlanStatus status) {
    return CliResponsePayloadMapper.planRejectionStatus(status);
  }
}
