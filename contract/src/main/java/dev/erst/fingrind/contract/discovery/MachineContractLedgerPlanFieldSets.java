package dev.erst.fingrind.contract.discovery;

import java.util.List;

/** Canonical field-set groupings for ledger-plan request descriptors and schemas. */
final class MachineContractLedgerPlanFieldSets {
  private MachineContractLedgerPlanFieldSets() {}

  static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractLedgerPlanStructureFieldSpecs.planIdField(),
        MachineContractLedgerPlanStructureFieldSpecs.stepsField());
  }

  static List<MachineContractFieldSpec> stepFields() {
    return List.of(
        MachineContractLedgerPlanStructureFieldSpecs.stepIdField(),
        MachineContractLedgerPlanStructureFieldSpecs.genericStepKindField(),
        MachineContractLedgerPlanStepPayloadFieldSpecs.conditionalPostingField(),
        MachineContractLedgerPlanStepPayloadFieldSpecs.conditionalDeclareAccountField(),
        MachineContractLedgerPlanStepPayloadFieldSpecs.conditionalDeclareTaxRegistrationField(),
        MachineContractLedgerPlanStepPayloadFieldSpecs.conditionalQueryField(),
        MachineContractLedgerPlanStepPayloadFieldSpecs.conditionalAssertionField(),
        MachineContractLedgerPlanStepPayloadFieldSpecs.conditionalPostingIdField());
  }

  static List<MachineContractFieldSpec> queryFields() {
    return List.of(
        MachineContractLedgerPlanQueryFieldSpecs.conditionalAccountCodeQueryField(),
        MachineContractLedgerPlanQueryFieldSpecs.conditionalEffectiveDateFromQueryField(),
        MachineContractLedgerPlanQueryFieldSpecs.conditionalEffectiveDateToQueryField(),
        MachineContractLedgerPlanQueryFieldSpecs.conditionalLimitField(),
        MachineContractLedgerPlanQueryFieldSpecs.conditionalCursorField());
  }

  static List<MachineContractFieldSpec> assertionFields() {
    return List.of(
        MachineContractLedgerPlanAssertionFieldSpecs.genericAssertionKindField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionAccountCodeField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionPostingIdField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionEffectiveDateFromField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionEffectiveDateToField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionNetAmountField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionBalanceSideField());
  }
}
