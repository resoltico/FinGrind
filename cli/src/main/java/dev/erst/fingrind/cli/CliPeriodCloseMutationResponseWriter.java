package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookLifecycleJsonModels;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.Objects;

/** Renders CLI results for reporting-period close mutations. */
final class CliPeriodCloseMutationResponseWriter {
  private final CliOutputChannel outputChannel;

  CliPeriodCloseMutationResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeInterimResultSweepResult(InterimResultSweepResult result, OutputMode outputMode) {
    switch (result) {
      case InterimResultSweepResult.Swept swept ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliBookLifecycleJsonModels.SweptInterimResultPayload(
                              swept.sweptInterimResult().sweepOrder(),
                              swept
                                  .sweptInterimResult()
                                  .reportingPeriod()
                                  .effectiveDateFrom()
                                  .toString(),
                              swept
                                  .sweptInterimResult()
                                  .reportingPeriod()
                                  .effectiveDateTo()
                                  .toString(),
                              swept.sweptInterimResult().resultHoldingAccountCode().value(),
                              swept.sweptInterimResult().sweptTotals().stream()
                                  .map(CliPayloadAssembler::balancePayload)
                                  .toList(),
                              swept.sweptInterimResult().sweptAt().toString(),
                              swept.sweptInterimResult().sweepPostingIds().stream()
                                  .map(dev.erst.fingrind.core.PostingId::value)
                                  .toList(),
                              CliAttestationCommitPresentation.requiredPayload(
                                  swept.attestationCommit())))),
              () ->
                  outputChannel.writeText(
                      CliPeriodCloseOutputRenderer.renderSweptInterimResultText(
                          swept.sweptInterimResult(), swept.attestationCommit())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.INTERIM_RESULT_SWEEP));
              });
      case InterimResultSweepResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(
                  OperationId.INTERIM_RESULT_SWEEP, rejected.rejection()),
              outputMode);
    }
  }

  void writeFiscalYearCloseResult(FiscalYearCloseResult result, OutputMode outputMode) {
    switch (result) {
      case FiscalYearCloseResult.Closed closed ->
          outputMode.run(
              () ->
                  outputChannel.writeEnvelope(
                      CliEnvelopeMapper.successEnvelope(
                          new CliBookLifecycleJsonModels.ClosedFiscalYearPayload(
                              closed.closedFiscalYear().closeOrder(),
                              closed
                                  .closedFiscalYear()
                                  .reportingPeriod()
                                  .effectiveDateFrom()
                                  .toString(),
                              closed
                                  .closedFiscalYear()
                                  .reportingPeriod()
                                  .effectiveDateTo()
                                  .toString(),
                              closed.closedFiscalYear().capitalAccountCode().value(),
                              closed.closedFiscalYear().resultHoldingAccountCode().value(),
                              closed.closedFiscalYear().retainedAccumulatedAccountCode().value(),
                              closed.closedFiscalYear().closedAt().toString(),
                              closed.idempotentReplay(),
                              closed.closedFiscalYear().closePostingIds().stream()
                                  .map(dev.erst.fingrind.core.PostingId::value)
                                  .toList(),
                              CliAttestationCommitPresentation.payload(
                                  closed.attestationCommit())))),
              () ->
                  outputChannel.writeText(
                      CliPeriodCloseOutputRenderer.renderClosedFiscalYearText(
                          closed.closedFiscalYear(),
                          closed.idempotentReplay(),
                          closed.attestationCommit())),
              () -> {
                throw new IllegalArgumentException(
                    CliOperationText.unsupportedCsvOutput(OperationId.FISCAL_YEAR_CLOSE));
              });
      case FiscalYearCloseResult.Rejected rejected ->
          outputChannel.writeRejectedEnvelope(
              CliRejectionPayloadMapper.administrationRejectedEnvelope(
                  OperationId.FISCAL_YEAR_CLOSE, rejected.rejection()),
              outputMode);
    }
  }
}
