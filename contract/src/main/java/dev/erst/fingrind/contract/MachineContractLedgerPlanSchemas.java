package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import dev.erst.fingrind.core.BalanceSide;
import java.util.List;
import java.util.Map;

/** Builds executable JSON Schema documents for ledger-plan request shapes. */
final class MachineContractLedgerPlanSchemas {
  private MachineContractLedgerPlanSchemas() {}

  static Map<String, Object> ledgerPlanSchema() {
    return MachineContractSchemaSupport.rootObjectSchema(
        "Canonical ledger-plan request JSON document.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Plan.PLAN_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Caller-supplied plan identifier."),
            ProtocolLedgerPlanFields.Plan.STEPS,
            MachineContractSchemaSupport.arraySchema(
                "Ordered non-empty list of executable ledger-plan steps.",
                stepSchema(),
                1,
                ProtocolLimits.LEDGER_PLAN_STEP_MAX)),
        List.of(ProtocolLedgerPlanFields.Plan.PLAN_ID, ProtocolLedgerPlanFields.Plan.STEPS));
  }

  private static Map<String, Object> stepSchema() {
    return MachineContractSchemaSupport.oneOfSchema(
        "One executable ledger-plan step.",
        List.of(
            simpleStepSchema(LedgerStepKind.OPEN_BOOK.wireValue()),
            stepWithDeclareAccount(),
            stepWithPosting(LedgerStepKind.PREFLIGHT_ENTRY.wireValue()),
            stepWithPosting(LedgerStepKind.POST_ENTRY.wireValue()),
            simpleStepSchema(LedgerStepKind.INSPECT_BOOK.wireValue()),
            stepWithQuery(
                LedgerStepKind.LIST_ACCOUNTS.wireValue(), listAccountsQuerySchema(), false),
            stepWithPostingId(),
            stepWithQuery(
                LedgerStepKind.LIST_POSTINGS.wireValue(), listPostingsQuerySchema(), false),
            stepWithQuery(
                LedgerStepKind.ACCOUNT_BALANCE.wireValue(), accountBalanceQuerySchema(), true),
            stepWithAssertion()));
  }

  private static Map<String, Object> simpleStepSchema(String kind) {
    return MachineContractSchemaSupport.objectSchema(
        "Ledger-plan step `" + kind + "`.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Caller-supplied step identifier."),
            ProtocolLedgerPlanFields.Step.KIND,
            MachineContractSchemaSupport.constSchema(kind, "Canonical ledger-plan step kind.")),
        List.of(ProtocolLedgerPlanFields.Step.STEP_ID, ProtocolLedgerPlanFields.Step.KIND));
  }

  private static Map<String, Object> stepWithDeclareAccount() {
    return MachineContractSchemaSupport.objectSchema(
        "Ledger-plan step `" + LedgerStepKind.DECLARE_ACCOUNT.wireValue() + "`.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Caller-supplied step identifier."),
            ProtocolLedgerPlanFields.Step.KIND,
            MachineContractSchemaSupport.constSchema(
                LedgerStepKind.DECLARE_ACCOUNT.wireValue(), "Canonical step kind."),
            ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT,
            MachineContractDeclareAccountSchemas.declareAccountSchemaWithoutDialect()),
        List.of(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            ProtocolLedgerPlanFields.Step.KIND,
            ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT));
  }

  private static Map<String, Object> stepWithPosting(String kind) {
    return MachineContractSchemaSupport.objectSchema(
        "Ledger-plan step `" + kind + "`.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Caller-supplied step identifier."),
            ProtocolLedgerPlanFields.Step.KIND,
            MachineContractSchemaSupport.constSchema(kind, "Canonical step kind."),
            ProtocolLedgerPlanFields.Step.POSTING,
            MachineContractPostEntrySchemas.postEntrySchemaWithoutDialect()),
        List.of(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            ProtocolLedgerPlanFields.Step.KIND,
            ProtocolLedgerPlanFields.Step.POSTING));
  }

  private static Map<String, Object> stepWithQuery(
      String kind, Map<String, Object> querySchema, boolean queryRequired) {
    return MachineContractSchemaSupport.objectSchema(
        "Ledger-plan step `" + kind + "`.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Caller-supplied step identifier."),
            ProtocolLedgerPlanFields.Step.KIND,
            MachineContractSchemaSupport.constSchema(kind, "Canonical step kind."),
            ProtocolLedgerPlanFields.Step.QUERY,
            querySchema),
        queryRequired
            ? List.of(
                ProtocolLedgerPlanFields.Step.STEP_ID,
                ProtocolLedgerPlanFields.Step.KIND,
                ProtocolLedgerPlanFields.Step.QUERY)
            : List.of(ProtocolLedgerPlanFields.Step.STEP_ID, ProtocolLedgerPlanFields.Step.KIND));
  }

  private static Map<String, Object> stepWithPostingId() {
    return MachineContractSchemaSupport.objectSchema(
        "Ledger-plan step `" + LedgerStepKind.GET_POSTING.wireValue() + "`.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Caller-supplied step identifier."),
            ProtocolLedgerPlanFields.Step.KIND,
            MachineContractSchemaSupport.constSchema(
                LedgerStepKind.GET_POSTING.wireValue(), "Canonical step kind."),
            ProtocolLedgerPlanFields.Step.POSTING_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Posting identifier to load.")),
        List.of(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            ProtocolLedgerPlanFields.Step.KIND,
            ProtocolLedgerPlanFields.Step.POSTING_ID));
  }

  private static Map<String, Object> stepWithAssertion() {
    return MachineContractSchemaSupport.objectSchema(
        "Ledger-plan assertion step.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            MachineContractSchemaSupport.nonBlankStringSchema("Caller-supplied step identifier."),
            ProtocolLedgerPlanFields.Step.KIND,
            MachineContractSchemaSupport.constSchema(
                LedgerStepKind.ASSERT.wireValue(), "Canonical assertion step kind."),
            ProtocolLedgerPlanFields.Step.ASSERTION,
            assertionSchema()),
        List.of(
            ProtocolLedgerPlanFields.Step.STEP_ID,
            ProtocolLedgerPlanFields.Step.KIND,
            ProtocolLedgerPlanFields.Step.ASSERTION));
  }

  private static Map<String, Object> listAccountsQuerySchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional "
            + MachineContractSchemaSupport.operation(OperationId.LIST_ACCOUNTS)
            + " query window.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Query.LIMIT,
            MachineContractSchemaSupport.pageLimitSchema(),
            ProtocolLedgerPlanFields.Query.CURSOR,
            MachineContractSchemaSupport.nonBlankStringSchema("Opaque account-page cursor.")),
        List.of());
  }

  private static Map<String, Object> listPostingsQuerySchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional posting-page filter and continuation window.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
            MachineContractSchemaSupport.nonBlankStringSchema("Optional account filter."),
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
            MachineContractSchemaSupport.dateStringSchema("Inclusive effective-date lower bound."),
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
            MachineContractSchemaSupport.dateStringSchema("Inclusive effective-date upper bound."),
            ProtocolLedgerPlanFields.Query.LIMIT,
            MachineContractSchemaSupport.pageLimitSchema(),
            ProtocolLedgerPlanFields.Query.CURSOR,
            MachineContractSchemaSupport.nonBlankStringSchema("Opaque posting-page cursor.")),
        List.of());
  }

  private static Map<String, Object> accountBalanceQuerySchema() {
    return MachineContractSchemaSupport.objectSchema(
        MachineContractSchemaSupport.operation(OperationId.ACCOUNT_BALANCE) + " query payload.",
        MachineContractSchemaSupport.orderedMap(
            ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
            MachineContractSchemaSupport.nonBlankStringSchema("Declared account code."),
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
            MachineContractSchemaSupport.dateStringSchema("Inclusive effective-date lower bound."),
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
            MachineContractSchemaSupport.dateStringSchema("Inclusive effective-date upper bound.")),
        List.of(ProtocolLedgerPlanFields.Query.ACCOUNT_CODE));
  }

  private static Map<String, Object> assertionSchema() {
    return MachineContractSchemaSupport.oneOfSchema(
        "Assertion payload nested inside an assert step.",
        List.of(
            MachineContractSchemaSupport.objectSchema(
                "Assertion `assert-account-declared`.",
                MachineContractSchemaSupport.orderedMap(
                    ProtocolLedgerPlanFields.Assertion.KIND,
                    MachineContractSchemaSupport.constSchema(
                        LedgerAssertionKind.ACCOUNT_DECLARED.wireValue(),
                        "Canonical assertion kind."),
                    ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE,
                    MachineContractSchemaSupport.nonBlankStringSchema("Declared account code.")),
                List.of(
                    ProtocolLedgerPlanFields.Assertion.KIND,
                    ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE)),
            MachineContractSchemaSupport.objectSchema(
                "Assertion `assert-account-active`.",
                MachineContractSchemaSupport.orderedMap(
                    ProtocolLedgerPlanFields.Assertion.KIND,
                    MachineContractSchemaSupport.constSchema(
                        LedgerAssertionKind.ACCOUNT_ACTIVE.wireValue(),
                        "Canonical assertion kind."),
                    ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE,
                    MachineContractSchemaSupport.nonBlankStringSchema("Declared account code.")),
                List.of(
                    ProtocolLedgerPlanFields.Assertion.KIND,
                    ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE)),
            MachineContractSchemaSupport.objectSchema(
                "Assertion `assert-posting-exists`.",
                MachineContractSchemaSupport.orderedMap(
                    ProtocolLedgerPlanFields.Assertion.KIND,
                    MachineContractSchemaSupport.constSchema(
                        LedgerAssertionKind.POSTING_EXISTS.wireValue(),
                        "Canonical assertion kind."),
                    ProtocolLedgerPlanFields.Assertion.POSTING_ID,
                    MachineContractSchemaSupport.nonBlankStringSchema(
                        "Posting identifier that must exist.")),
                List.of(
                    ProtocolLedgerPlanFields.Assertion.KIND,
                    ProtocolLedgerPlanFields.Assertion.POSTING_ID)),
            MachineContractSchemaSupport.objectSchema(
                "Assertion `assert-account-balance`.",
                MachineContractSchemaSupport.orderedMap(
                    ProtocolLedgerPlanFields.Assertion.KIND,
                    MachineContractSchemaSupport.constSchema(
                        LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS.wireValue(),
                        "Canonical assertion kind."),
                    ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE,
                    MachineContractSchemaSupport.nonBlankStringSchema("Declared account code."),
                    ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM,
                    MachineContractSchemaSupport.dateStringSchema(
                        "Inclusive effective-date lower bound."),
                    ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO,
                    MachineContractSchemaSupport.dateStringSchema(
                        "Inclusive effective-date upper bound."),
                    ProtocolLedgerPlanFields.Assertion.CURRENCY_CODE,
                    MachineContractSchemaSupport.nonBlankStringSchema("Currency bucket to assert."),
                    ProtocolLedgerPlanFields.Assertion.NET_AMOUNT,
                    MachineContractSchemaSupport.decimalAmountStringSchema(
                        "Expected plain-decimal net amount."),
                    ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE,
                    MachineContractSchemaSupport.enumStringSchema(
                        "Expected balance side.", BalanceSide.wireValues())),
                List.of(
                    ProtocolLedgerPlanFields.Assertion.KIND,
                    ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE,
                    ProtocolLedgerPlanFields.Assertion.CURRENCY_CODE,
                    ProtocolLedgerPlanFields.Assertion.NET_AMOUNT,
                    ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE))));
  }
}
