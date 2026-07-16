package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;

/** Renders a compact human outline of the canonical ledger-plan template. */
final class CliDiscoveryPlanTemplateGuidance {
  private CliDiscoveryPlanTemplateGuidance() {}

  static String render(ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    String steps =
        String.join(
            ", ",
            planTemplate.steps().stream()
                .map(step -> step.stepId() + " (" + step.kind().wireValue() + ")")
                .toList());
    return CliTextFormat.renderKeyValueBlock(
        java.util.List.of(
            java.util.List.of("planId", planTemplate.planId()), java.util.List.of("steps", steps)),
        CliDiscoveryTextSupport.TEXT_WRAP_WIDTH,
        32);
  }
}
