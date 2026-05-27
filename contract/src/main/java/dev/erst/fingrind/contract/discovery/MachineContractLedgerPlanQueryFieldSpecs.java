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
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
        description,
        MachineContractScalarSchemas.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec optionalAccountCodeQueryField() {
    return MachineContractFieldSpec.optional(
        conditionalAccountCodeQueryField().name(),
        conditionalAccountCodeQueryField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalAccountCodeQueryField()));
  }

  static MachineContractFieldSpec requiredAccountCodeQueryField() {
    return MachineContractFieldSpec.required(
        conditionalAccountCodeQueryField().name(),
        conditionalAccountCodeQueryField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalAccountCodeQueryField()));
  }

  static MachineContractFieldSpec conditionalEffectiveDateFromQueryField() {
    String description = "Inclusive ISO-8601 effective-date lower bound.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
        description,
        MachineContractScalarSchemas.dateStringSchema(description));
  }

  static MachineContractFieldSpec optionalEffectiveDateFromQueryField() {
    return MachineContractFieldSpec.optional(
        conditionalEffectiveDateFromQueryField().name(),
        conditionalEffectiveDateFromQueryField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(
            conditionalEffectiveDateFromQueryField()));
  }

  static MachineContractFieldSpec conditionalEffectiveDateToQueryField() {
    String description = "Inclusive ISO-8601 effective-date upper bound.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
        description,
        MachineContractScalarSchemas.dateStringSchema(description));
  }

  static MachineContractFieldSpec optionalEffectiveDateToQueryField() {
    return MachineContractFieldSpec.optional(
        conditionalEffectiveDateToQueryField().name(),
        conditionalEffectiveDateToQueryField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(
            conditionalEffectiveDateToQueryField()));
  }

  static MachineContractFieldSpec conditionalLimitField() {
    String description = "Page size for list steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.LIMIT,
        description,
        MachineContractScalarSchemas.pageLimitSchema());
  }

  static MachineContractFieldSpec optionalLimitField() {
    return MachineContractFieldSpec.optional(
        conditionalLimitField().name(),
        conditionalLimitField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalLimitField()));
  }

  static MachineContractFieldSpec conditionalCursorField() {
    String description =
        "Opaque cursor for continuing "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.LIST_POSTINGS)
            + " or "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.LIST_ACCOUNTS)
            + " from the prior page.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.CURSOR,
        description,
        MachineContractScalarSchemas.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec optionalCursorField() {
    return MachineContractFieldSpec.optional(
        conditionalCursorField().name(),
        conditionalCursorField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalCursorField()));
  }
}
