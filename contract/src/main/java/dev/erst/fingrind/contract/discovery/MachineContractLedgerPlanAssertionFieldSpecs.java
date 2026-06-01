package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.core.BalanceSide;

/** Assertion-payload field specifications for executable ledger plans. */
final class MachineContractLedgerPlanAssertionFieldSpecs {
  private MachineContractLedgerPlanAssertionFieldSpecs() {}

  static MachineContractFieldSpec genericAssertionKindField() {
    String description = "Canonical assertion kind nested inside an assert step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Assertion.KIND,
        description,
        MachineContractScalarSchemas.enumStringSchema(
            description, LedgerAssertionKind.wireValues()));
  }

  static MachineContractFieldSpec requiredConstAssertionKindField(LedgerAssertionKind kind) {
    String description = "Canonical assertion kind nested inside an assert step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Assertion.KIND,
        description,
        MachineContractScalarSchemas.constSchema(kind.wireValue(), description));
  }

  static MachineContractFieldSpec conditionalAssertionAccountCodeField() {
    String description = "Account code consumed by account assertions.";
    return MachineContractLedgerPlanFieldSpecs.conditionalNonBlankStringField(
        ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE, description);
  }

  static MachineContractFieldSpec requiredAssertionAccountCodeField() {
    return MachineContractLedgerPlanFieldSpecs.requiredFromConditional(
        conditionalAssertionAccountCodeField());
  }

  static MachineContractFieldSpec conditionalAssertionPostingIdField() {
    String description = "Posting identifier consumed by posting-existence assertions.";
    return MachineContractLedgerPlanFieldSpecs.conditionalNonBlankStringField(
        ProtocolLedgerPlanFields.Assertion.POSTING_ID, description);
  }

  static MachineContractFieldSpec requiredAssertionPostingIdField() {
    return MachineContractLedgerPlanFieldSpecs.requiredFromConditional(
        conditionalAssertionPostingIdField());
  }

  static MachineContractFieldSpec conditionalAssertionEffectiveDateFromField() {
    String description = "Inclusive ISO-8601 effective-date lower bound for balance assertions.";
    return MachineContractLedgerPlanFieldSpecs.conditionalDateField(
        ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM, description);
  }

  static MachineContractFieldSpec optionalAssertionEffectiveDateFromField() {
    return MachineContractLedgerPlanFieldSpecs.optionalFromConditional(
        conditionalAssertionEffectiveDateFromField());
  }

  static MachineContractFieldSpec conditionalAssertionEffectiveDateToField() {
    String description = "Inclusive ISO-8601 effective-date upper bound for balance assertions.";
    return MachineContractLedgerPlanFieldSpecs.conditionalDateField(
        ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO, description);
  }

  static MachineContractFieldSpec optionalAssertionEffectiveDateToField() {
    return MachineContractLedgerPlanFieldSpecs.optionalFromConditional(
        conditionalAssertionEffectiveDateToField());
  }

  static MachineContractFieldSpec conditionalAssertionNetAmountField() {
    String description =
        "Exact expected net money object for "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.ACCOUNT_BALANCE)
            + " assertions.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Assertion.NET_AMOUNT,
        description,
        MachineContractScalarSchemas.moneyObjectSchema(description, false));
  }

  static MachineContractFieldSpec requiredAssertionNetAmountField() {
    return MachineContractFieldSpec.required(
        conditionalAssertionNetAmountField().name(),
        conditionalAssertionNetAmountField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalAssertionNetAmountField()));
  }

  static MachineContractFieldSpec conditionalAssertionBalanceSideField() {
    String description =
        "Expected normal-balance side for "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.ACCOUNT_BALANCE)
            + " assertions.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE,
        description,
        MachineContractScalarSchemas.enumStringSchema(description, BalanceSide.wireValues()));
  }

  static MachineContractFieldSpec requiredAssertionBalanceSideField() {
    return MachineContractFieldSpec.required(
        conditionalAssertionBalanceSideField().name(),
        conditionalAssertionBalanceSideField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(
            conditionalAssertionBalanceSideField()));
  }
}
