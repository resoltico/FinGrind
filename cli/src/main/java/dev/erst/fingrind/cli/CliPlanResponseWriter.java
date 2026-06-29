package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import java.util.Objects;

/** Renders ledger-plan execution results through the shared output channel. */
final class CliPlanResponseWriter {
  private final CliOutputChannel outputChannel;

  CliPlanResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeLedgerPlanResult(
      LedgerPlanResult result, OutputMode outputMode, PlanResultDetail resultDetail) {
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(outputMode, "outputMode");
    Objects.requireNonNull(resultDetail, "resultDetail");
    if (outputMode == OutputMode.TEXT) {
      outputChannel.writeText(CliPlanTextRenderer.renderLedgerPlanResult(result, resultDetail));
      return;
    }
    outputChannel.writeEnvelope(CliEnvelopeMapper.ledgerPlanEnvelope(result, resultDetail));
  }
}
