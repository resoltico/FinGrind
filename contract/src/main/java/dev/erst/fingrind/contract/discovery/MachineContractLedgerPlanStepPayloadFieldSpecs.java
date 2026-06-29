package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolOpenBookFields;
import java.util.List;
import java.util.Map;

/** Step-payload field specifications for executable ledger plans. */
final class MachineContractLedgerPlanStepPayloadFieldSpecs {
  private MachineContractLedgerPlanStepPayloadFieldSpecs() {}

  static MachineContractFieldSpec conditionalEnsureBookField() {
    String description = "Replay-safe setup payload for ensure-book steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.ENSURE_BOOK, description, ensureBookSchema());
  }

  static MachineContractFieldSpec requiredEnsureBookField() {
    return MachineContractFieldSpec.required(
        conditionalEnsureBookField().name(),
        conditionalEnsureBookField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalEnsureBookField()));
  }

  static MachineContractFieldSpec conditionalPostingField() {
    String description =
        "Posting request payload for "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.PREFLIGHT_ENTRY)
            + ", "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.RECORD_SALE)
            + ", "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.RECORD_EXPENSE)
            + ", "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.RECORD_OWNER_CONTRIBUTION)
            + ", "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.RECORD_OWNER_WITHDRAWAL)
            + ", "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.RECORD_OPENING_POSITION)
            + ", "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.RECORD_REVERSAL)
            + ", and "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.POST_ENTRY)
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
        MachineContractScalarSchemas.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec requiredPostingIdField() {
    return MachineContractFieldSpec.required(
        conditionalPostingIdField().name(),
        conditionalPostingIdField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(conditionalPostingIdField()));
  }

  private static Map<String, Object> ensureBookSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Replay-safe setup payload for ensure-book steps.",
        List.of(
            MachineContractFieldSpec.required(
                ProtocolOpenBookFields.ENTITY_NAME,
                "Accounting entity name for the selected protected book.",
                MachineContractScalarSchemas.nonBlankStringSchema(
                    "Accounting entity name for the selected protected book.")),
            MachineContractFieldSpec.required(
                ProtocolOpenBookFields.FUNCTIONAL_CURRENCY,
                "Three-letter ISO functional currency code for the selected book.",
                MachineContractScalarSchemas.currencyCodeSchema(
                    "Three-letter ISO functional currency code for the selected book.")),
            MachineContractFieldSpec.required(
                ProtocolOpenBookFields.FISCAL_YEAR_START,
                "Fiscal year start encoded as MM-dd.",
                MachineContractScalarSchemas.nonBlankStringSchema(
                    "Fiscal year start encoded as MM-dd."))));
  }
}
