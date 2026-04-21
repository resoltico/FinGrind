package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolStatuses;
import java.nio.file.Path;
import java.util.Objects;

/** Renders query and reporting CLI results through the shared output channel. */
final class CliQueryResponseSupport {
  private final CliOutputChannel outputChannel;

  CliQueryResponseSupport(CliOutputChannel outputChannel) {
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
    result.fold(
        listed -> {
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
          return Boolean.TRUE;
        },
        rejected -> {
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return Boolean.FALSE;
        });
  }

  void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
    result.fold(
        found -> {
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
          return Boolean.TRUE;
        },
        rejected -> {
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return Boolean.FALSE;
        });
  }

  void writeListPostingsResult(ListPostingsResult result, OutputMode outputMode) {
    result.fold(
        listed -> {
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
          return Boolean.TRUE;
        },
        rejected -> {
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return Boolean.FALSE;
        });
  }

  void writeAccountBalanceResult(AccountBalanceResult result, OutputMode outputMode) {
    result.fold(
        reported -> {
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
          return Boolean.TRUE;
        },
        rejected -> {
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return Boolean.FALSE;
        });
  }

  void writeTrialBalanceResult(TrialBalanceResult result, OutputMode outputMode) {
    result.fold(
        reported -> {
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
          return Boolean.TRUE;
        },
        rejected -> {
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return Boolean.FALSE;
        });
  }

  void writeAccountLedgerResult(AccountLedgerResult result, OutputMode outputMode) {
    result.fold(
        reported -> {
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
          return Boolean.TRUE;
        },
        rejected -> {
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return Boolean.FALSE;
        });
  }

  void writePeriodSummaryResult(PeriodSummaryResult result, OutputMode outputMode) {
    result.fold(
        reported -> {
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
          return Boolean.TRUE;
        },
        rejected -> {
          outputChannel.writeQueryRejection(
              outputMode, CliResponsePayloadMapper.queryRejectedEnvelope(rejected.rejection()));
          return Boolean.FALSE;
        });
  }

  void writeLedgerPlanResult(LedgerPlanResult result) {
    Object envelope =
        switch (result) {
          case LedgerPlanResult.Succeeded succeeded ->
              new CliEnvelopeJsonModels.SuccessEnvelope(
                  ProtocolStatuses.PLAN_COMMITTED,
                  CliResponsePayloadMapper.ledgerPlanPayload(succeeded));
          case LedgerPlanResult.Rejected rejected ->
              CliResponsePayloadMapper.rejectedPlanEnvelope(
                  rejected, ProtocolStatuses.PLAN_REJECTED);
          case LedgerPlanResult.AssertionFailed assertionFailed ->
              CliResponsePayloadMapper.rejectedPlanEnvelope(
                  assertionFailed, ProtocolStatuses.PLAN_ASSERTION_FAILED);
        };
    outputChannel.writeEnvelope(envelope);
  }

  static String planRejectionStatus(LedgerPlanStatus status) {
    return CliResponsePayloadMapper.planRejectionStatus(status);
  }
}
