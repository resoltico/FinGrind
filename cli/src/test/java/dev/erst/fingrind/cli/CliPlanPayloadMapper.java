package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;

/** Test-only compatibility façade for legacy ledger-plan payload assertions. */
final class CliPlanPayloadMapper {
  private CliPlanPayloadMapper() {}

  static CliPlanJsonModels.LedgerPlanPayload ledgerPlanPayload(
      LedgerPlanResult result, PlanResultDetail resultDetail) {
    return CliLedgerPlanPayloadMapper.ledgerPlanPayload(result, resultDetail);
  }
}
