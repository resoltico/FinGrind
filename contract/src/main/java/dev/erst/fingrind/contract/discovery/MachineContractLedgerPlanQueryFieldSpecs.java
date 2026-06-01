package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;

/** Query-window field specifications for executable ledger plans. */
final class MachineContractLedgerPlanQueryFieldSpecs {
  private MachineContractLedgerPlanQueryFieldSpecs() {}

  static MachineContractFieldSpec conditionalAccountCodeQueryField() {
    String description =
        "Account filter for "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.LIST_POSTINGS)
            + " or required account target for "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.ACCOUNT_BALANCE)
            + ".";
    return MachineContractLedgerPlanFieldSpecs.conditionalNonBlankStringField(
        ProtocolLedgerPlanFields.Query.ACCOUNT_CODE, description);
  }

  static MachineContractFieldSpec optionalAccountCodeQueryField() {
    return MachineContractLedgerPlanFieldSpecs.optionalFromConditional(
        conditionalAccountCodeQueryField());
  }

  static MachineContractFieldSpec requiredAccountCodeQueryField() {
    return MachineContractLedgerPlanFieldSpecs.requiredFromConditional(
        conditionalAccountCodeQueryField());
  }

  static MachineContractFieldSpec conditionalEffectiveDateFromQueryField() {
    String description = "Inclusive ISO-8601 effective-date lower bound.";
    return MachineContractLedgerPlanFieldSpecs.conditionalDateField(
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM, description);
  }

  static MachineContractFieldSpec optionalEffectiveDateFromQueryField() {
    return MachineContractLedgerPlanFieldSpecs.optionalFromConditional(
        conditionalEffectiveDateFromQueryField());
  }

  static MachineContractFieldSpec conditionalEffectiveDateToQueryField() {
    String description = "Inclusive ISO-8601 effective-date upper bound.";
    return MachineContractLedgerPlanFieldSpecs.conditionalDateField(
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO, description);
  }

  static MachineContractFieldSpec optionalEffectiveDateToQueryField() {
    return MachineContractLedgerPlanFieldSpecs.optionalFromConditional(
        conditionalEffectiveDateToQueryField());
  }

  static MachineContractFieldSpec conditionalLimitField() {
    String description = "Page size for list steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.LIMIT,
        description,
        MachineContractScalarSchemas.pageLimitSchema());
  }

  static MachineContractFieldSpec optionalLimitField() {
    return MachineContractLedgerPlanFieldSpecs.optionalFromConditional(conditionalLimitField());
  }

  static MachineContractFieldSpec conditionalCursorField() {
    String description =
        "Opaque cursor for continuing "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.LIST_POSTINGS)
            + " or "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.LIST_ACCOUNTS)
            + " from the prior page.";
    return MachineContractLedgerPlanFieldSpecs.conditionalNonBlankStringField(
        ProtocolLedgerPlanFields.Query.CURSOR, description);
  }

  static MachineContractFieldSpec optionalCursorField() {
    return MachineContractLedgerPlanFieldSpecs.optionalFromConditional(conditionalCursorField());
  }
}
