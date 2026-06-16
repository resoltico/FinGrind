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
    boolean succeeded = result instanceof LedgerPlanResult.Succeeded;
    if (outputMode == OutputMode.TEXT) {
      String rendered = CliPlanTextRenderer.renderLedgerPlanResult(result, resultDetail);
      if (succeeded) {
        outputChannel.writeText(rendered);
      } else {
        outputChannel.writeFailureText(rendered);
      }
      return;
    }
    if (succeeded) {
      outputChannel.writeEnvelope(CliEnvelopeMapper.ledgerPlanEnvelope(result, resultDetail));
      return;
    }
    outputChannel.writeDiagnosticEnvelope(
        CliEnvelopeMapper.ledgerPlanEnvelope(result, resultDetail));
  }
}
