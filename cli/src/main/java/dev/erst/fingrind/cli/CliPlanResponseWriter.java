package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import java.util.Objects;

/** Renders ledger-plan execution results through the shared output channel. */
final class CliPlanResponseWriter {
  private final CliOutputChannel outputChannel;

  CliPlanResponseWriter(CliOutputChannel outputChannel) {
    this.outputChannel = Objects.requireNonNull(outputChannel, "outputChannel");
  }

  void writeLedgerPlanResult(LedgerPlanResult result, PlanResultDetail resultDetail) {
    outputChannel.writeEnvelope(CliEnvelopeMapper.ledgerPlanEnvelope(result, resultDetail));
  }
}
