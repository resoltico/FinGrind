package dev.erst.fingrind.contract;

import java.util.List;

/** Canonical field-set groupings for ledger-plan request descriptors and schemas. */
final class MachineContractLedgerPlanFieldSets {
  private MachineContractLedgerPlanFieldSets() {}

  static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractLedgerPlanStepQueryFieldSpecs.planIdField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.stepsField());
  }

  static List<MachineContractFieldSpec> stepFields() {
    return List.of(
        MachineContractLedgerPlanStepQueryFieldSpecs.stepIdField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.genericStepKindField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalPostingField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalDeclareAccountField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalQueryField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalAssertionField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalPostingIdField());
  }

  static List<MachineContractFieldSpec> queryFields() {
    return List.of(
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalAccountCodeQueryField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalEffectiveDateFromQueryField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalEffectiveDateToQueryField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalLimitField(),
        MachineContractLedgerPlanStepQueryFieldSpecs.conditionalCursorField());
  }

  static List<MachineContractFieldSpec> assertionFields() {
    return List.of(
        MachineContractLedgerPlanAssertionFieldSpecs.genericAssertionKindField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionAccountCodeField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionPostingIdField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionEffectiveDateFromField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionEffectiveDateToField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionCurrencyCodeField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionNetAmountField(),
        MachineContractLedgerPlanAssertionFieldSpecs.conditionalAssertionBalanceSideField());
  }
}
