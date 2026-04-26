package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import java.util.Map;

/** Step-level and query-level field specifications for executable ledger plans. */
final class MachineContractLedgerPlanStepQueryFieldSpecs {
  private MachineContractLedgerPlanStepQueryFieldSpecs() {}

  static MachineContractFieldSpec planIdField() {
    String description = "Caller-supplied plan identifier.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Plan.PLAN_ID,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec stepsField() {
    String description = "Ordered non-empty array of executable ledger-plan steps.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Plan.STEPS,
        description,
        MachineContractSchemaSupport.arraySchema(
            description,
            MachineContractLedgerPlanVariantSchemas.stepSchema(),
            1,
            ProtocolLimits.LEDGER_PLAN_STEP_MAX));
  }

  static MachineContractFieldSpec stepIdField() {
    String description = "Caller-supplied step identifier unique inside the plan.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Step.STEP_ID,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec genericStepKindField() {
    String description = "Canonical ledger-plan step kind for this step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Step.KIND,
        description,
        MachineContractSchemaSupport.enumStringSchema(description, LedgerStepKind.wireValues()));
  }

  static MachineContractFieldSpec requiredConstStepKindField(LedgerStepKind kind) {
    String description = "Canonical ledger-plan step kind for this step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Step.KIND,
        description,
        MachineContractSchemaSupport.constSchema(kind.wireValue(), description));
  }

  static MachineContractFieldSpec conditionalPostingField() {
    String description =
        "Posting request payload for "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.POST_ENTRY)
            + " and "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.PREFLIGHT_ENTRY)
            + " steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.POSTING,
        description,
        MachineContractPostEntrySchemas.postEntrySchemaWithoutDialect());
  }

  static MachineContractFieldSpec requiredPostingField() {
    return MachineContractFieldSpec.required(
        conditionalPostingField().name(),
        conditionalPostingField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalPostingField()));
  }

  static MachineContractFieldSpec conditionalDeclareAccountField() {
    String description =
        "Account declaration payload for "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.DECLARE_ACCOUNT)
            + " steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT,
        description,
        MachineContractDeclareAccountSchemas.declareAccountSchemaWithoutDialect());
  }

  static MachineContractFieldSpec requiredDeclareAccountField() {
    return MachineContractFieldSpec.required(
        conditionalDeclareAccountField().name(),
        conditionalDeclareAccountField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalDeclareAccountField()));
  }

  static MachineContractFieldSpec conditionalQueryField() {
    String description =
        "Query payload for list steps and "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.ACCOUNT_BALANCE)
            + " steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.QUERY,
        description,
        MachineContractLedgerPlanVariantSchemas.accountBalanceQuerySchema());
  }

  static MachineContractFieldSpec optionalQueryField(Map<String, Object> querySchema) {
    return MachineContractFieldSpec.optional(
        conditionalQueryField().name(), conditionalQueryField().description(), querySchema);
  }

  static MachineContractFieldSpec requiredQueryField(Map<String, Object> querySchema) {
    return MachineContractFieldSpec.required(
        conditionalQueryField().name(), conditionalQueryField().description(), querySchema);
  }

  static MachineContractFieldSpec conditionalAssertionField() {
    String description = "Assertion payload for first-class assertion steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.ASSERTION,
        description,
        MachineContractLedgerPlanVariantSchemas.assertionSchema());
  }

  static MachineContractFieldSpec requiredAssertionField() {
    return MachineContractFieldSpec.required(
        conditionalAssertionField().name(),
        conditionalAssertionField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalAssertionField()));
  }

  static MachineContractFieldSpec conditionalPostingIdField() {
    String description =
        "Posting identifier for "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.GET_POSTING)
            + " steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.POSTING_ID,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec requiredPostingIdField() {
    return MachineContractFieldSpec.required(
        conditionalPostingIdField().name(),
        conditionalPostingIdField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalPostingIdField()));
  }

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
        MachineContractSchemaSupport.nonBlankStringSchema(description));
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
        MachineContractSchemaSupport.dateStringSchema(description));
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
        MachineContractSchemaSupport.dateStringSchema(description));
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
        MachineContractSchemaSupport.pageLimitSchema());
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
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec optionalCursorField() {
    return MachineContractFieldSpec.optional(
        conditionalCursorField().name(),
        conditionalCursorField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalCursorField()));
  }
}
