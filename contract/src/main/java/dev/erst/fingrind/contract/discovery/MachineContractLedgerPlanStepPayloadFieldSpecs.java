package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolOpenBookFields;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.InventoryCostingDoctrine;
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
    String description = "Posting request payload for preflight and every posting commit step.";
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

  static MachineContractFieldSpec conditionalDeclareTaxRegistrationField() {
    String description =
        "Tax registration declaration payload for "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.DECLARE_TAX_REGISTRATION)
            + " steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.DECLARE_TAX_REGISTRATION,
        description,
        MachineContractDeclareTaxRegistrationSchemas.declareTaxRegistrationSchemaWithoutDialect());
  }

  static MachineContractFieldSpec requiredDeclareTaxRegistrationField() {
    return MachineContractFieldSpec.required(
        conditionalDeclareTaxRegistrationField().name(),
        conditionalDeclareTaxRegistrationField().description(),
        MachineContractLedgerPlanFieldSupport.acceptedSchema(
            conditionalDeclareTaxRegistrationField()));
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
                ProtocolOpenBookFields.BOOK_TEMPLATE_ID,
                "Seed template persisted on the selected protected book.",
                MachineContractScalarSchemas.enumStringSchema(
                    "Seed template persisted on the selected protected book.",
                    BookTemplateId.wireValues())),
            MachineContractFieldSpec.required(
                ProtocolOpenBookFields.ACCOUNTING_BASIS,
                "Accounting basis persisted on the selected protected book.",
                MachineContractScalarSchemas.enumStringSchema(
                    "Accounting basis persisted on the selected protected book.",
                    AccountingBasis.wireValues())),
            MachineContractFieldSpec.conditional(
                ProtocolOpenBookFields.INVENTORY_COSTING,
                "Inventory costing doctrine required when bookTemplateId is OWNER_MANAGED_TRADING and forbidden when it is OWNER_MANAGED_SERVICE.",
                MachineContractScalarSchemas.enumStringSchema(
                    "Inventory costing doctrine required when bookTemplateId is OWNER_MANAGED_TRADING and forbidden when it is OWNER_MANAGED_SERVICE.",
                    InventoryCostingDoctrine.wireValues())),
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
