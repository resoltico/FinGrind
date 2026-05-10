package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Variant schema builders for executable ledger-plan steps, queries, and assertions. */
final class MachineContractLedgerPlanVariantSchemas {
  private MachineContractLedgerPlanVariantSchemas() {}

  static Map<String, Object> stepSchema() {
    return MachineContractSchemaSupport.oneOfSchema(
        "One executable ledger-plan step.",
        Arrays.stream(LedgerStepKind.values())
            .map(MachineContractLedgerPlanVariantSchemas::stepVariantSchema)
            .toList());
  }

  static Map<String, Object> accountBalanceQuerySchema() {
    return MachineContractSchemaSupport.objectSchema(
        MachineContractLedgerPlanFieldSupport.operation(OperationId.ACCOUNT_BALANCE)
            + " query payload.",
        List.of(
            MachineContractLedgerPlanStepQueryFieldSpecs.requiredAccountCodeQueryField(),
            MachineContractLedgerPlanStepQueryFieldSpecs.optionalEffectiveDateFromQueryField(),
            MachineContractLedgerPlanStepQueryFieldSpecs.optionalEffectiveDateToQueryField()));
  }

  static Map<String, Object> assertionSchema() {
    return MachineContractSchemaSupport.oneOfSchema(
        "Assertion payload nested inside an assert step.",
        Arrays.stream(LedgerAssertionKind.values())
            .map(MachineContractLedgerPlanVariantSchemas::assertionVariantSchema)
            .toList());
  }

  private static Map<String, Object> stepVariantSchema(LedgerStepKind kind) {
    return switch (kind) {
      case OPEN_BOOK, INSPECT_BOOK ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanStepQueryFieldSpecs.stepIdField(),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredConstStepKindField(kind)));
      case DECLARE_ACCOUNT ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanStepQueryFieldSpecs.stepIdField(),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredConstStepKindField(kind),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredDeclareAccountField()));
      case PREFLIGHT_ENTRY, POST_ENTRY ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanStepQueryFieldSpecs.stepIdField(),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredConstStepKindField(kind),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredPostingField()));
      case LIST_ACCOUNTS ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanStepQueryFieldSpecs.stepIdField(),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredConstStepKindField(kind),
                  MachineContractLedgerPlanStepQueryFieldSpecs.optionalQueryField(
                      listAccountsQuerySchema())));
      case GET_POSTING ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanStepQueryFieldSpecs.stepIdField(),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredConstStepKindField(kind),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredPostingIdField()));
      case LIST_POSTINGS ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanStepQueryFieldSpecs.stepIdField(),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredConstStepKindField(kind),
                  MachineContractLedgerPlanStepQueryFieldSpecs.optionalQueryField(
                      listPostingsQuerySchema())));
      case ACCOUNT_BALANCE ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanStepQueryFieldSpecs.stepIdField(),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredConstStepKindField(kind),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredQueryField(
                      accountBalanceQuerySchema())));
      case ASSERT ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan assertion step.",
              List.of(
                  MachineContractLedgerPlanStepQueryFieldSpecs.stepIdField(),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredConstStepKindField(kind),
                  MachineContractLedgerPlanStepQueryFieldSpecs.requiredAssertionField()));
    };
  }

  private static Map<String, Object> listAccountsQuerySchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional "
            + MachineContractLedgerPlanFieldSupport.operation(OperationId.LIST_ACCOUNTS)
            + " query window.",
        List.of(
            MachineContractLedgerPlanStepQueryFieldSpecs.optionalLimitField(),
            MachineContractLedgerPlanStepQueryFieldSpecs.optionalCursorField()));
  }

  private static Map<String, Object> listPostingsQuerySchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional posting-page filter and continuation window.",
        List.of(
            MachineContractLedgerPlanStepQueryFieldSpecs.optionalAccountCodeQueryField(),
            MachineContractLedgerPlanStepQueryFieldSpecs.optionalEffectiveDateFromQueryField(),
            MachineContractLedgerPlanStepQueryFieldSpecs.optionalEffectiveDateToQueryField(),
            MachineContractLedgerPlanStepQueryFieldSpecs.optionalLimitField(),
            MachineContractLedgerPlanStepQueryFieldSpecs.optionalCursorField()));
  }

  private static Map<String, Object> assertionVariantSchema(LedgerAssertionKind kind) {
    return switch (kind) {
      case ACCOUNT_DECLARED ->
          MachineContractSchemaSupport.objectSchema(
              "Assertion `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanAssertionFieldSpecs.requiredConstAssertionKindField(
                      kind),
                  MachineContractLedgerPlanAssertionFieldSpecs
                      .requiredAssertionAccountCodeField()));
      case ACCOUNT_ACTIVE ->
          MachineContractSchemaSupport.objectSchema(
              "Assertion `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanAssertionFieldSpecs.requiredConstAssertionKindField(
                      kind),
                  MachineContractLedgerPlanAssertionFieldSpecs
                      .requiredAssertionAccountCodeField()));
      case POSTING_EXISTS ->
          MachineContractSchemaSupport.objectSchema(
              "Assertion `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanAssertionFieldSpecs.requiredConstAssertionKindField(
                      kind),
                  MachineContractLedgerPlanAssertionFieldSpecs.requiredAssertionPostingIdField()));
      case ACCOUNT_BALANCE_EQUALS ->
          MachineContractSchemaSupport.objectSchema(
              "Assertion `" + kind.wireValue() + "`.",
              List.of(
                  MachineContractLedgerPlanAssertionFieldSpecs.requiredConstAssertionKindField(
                      kind),
                  MachineContractLedgerPlanAssertionFieldSpecs.requiredAssertionAccountCodeField(),
                  MachineContractLedgerPlanAssertionFieldSpecs
                      .optionalAssertionEffectiveDateFromField(),
                  MachineContractLedgerPlanAssertionFieldSpecs
                      .optionalAssertionEffectiveDateToField(),
                  MachineContractLedgerPlanAssertionFieldSpecs.requiredAssertionNetAmountField(),
                  MachineContractLedgerPlanAssertionFieldSpecs
                      .requiredAssertionBalanceSideField()));
    };
  }
}
