package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.ContractResponse.PlanExecutionDescriptor;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import dev.erst.fingrind.core.BalanceSide;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds executable JSON Schema documents for ledger-plan request shapes. */
final class MachineContractLedgerPlanSchemas {
  /** Internal discovery grouping for canonical ledger-plan step kinds. */
  private enum StepRole {
    ADMINISTRATION,
    QUERY,
    WRITE,
    ASSERT
  }

  private MachineContractLedgerPlanSchemas() {}

  static Map<String, Object> ledgerPlanSchema() {
    return MachineContractSchemaSupport.rootObjectSchema(
        "Canonical ledger-plan request JSON document.", topLevelFields());
  }

  static ContractRequestShapes.LedgerPlanRequestShapeDescriptor descriptor() {
    PlanExecutionDescriptor execution = MachineContractDescriptors.planExecution();
    return new ContractRequestShapes.LedgerPlanRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(topLevelFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(stepFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(queryFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(assertionFields()),
        stepKinds(StepRole.ADMINISTRATION),
        stepKinds(StepRole.QUERY),
        stepKinds(StepRole.WRITE),
        LedgerStepKind.ASSERT,
        Arrays.stream(LedgerAssertionKind.values()).toList(),
        execution,
        ledgerPlanSchema());
  }

  private static Map<String, Object> stepSchema() {
    return MachineContractSchemaSupport.oneOfSchema(
        "One executable ledger-plan step.",
        Arrays.stream(LedgerStepKind.values())
            .map(MachineContractLedgerPlanSchemas::stepVariantSchema)
            .toList());
  }

  private static Map<String, Object> stepVariantSchema(LedgerStepKind kind) {
    return switch (kind) {
      case OPEN_BOOK, INSPECT_BOOK ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(stepIdField(), requiredConstStepKindField(kind)));
      case DECLARE_ACCOUNT ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  stepIdField(), requiredConstStepKindField(kind), requiredDeclareAccountField()));
      case PREFLIGHT_ENTRY, POST_ENTRY ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(stepIdField(), requiredConstStepKindField(kind), requiredPostingField()));
      case LIST_ACCOUNTS ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  stepIdField(),
                  requiredConstStepKindField(kind),
                  optionalQueryField(listAccountsQuerySchema())));
      case GET_POSTING ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(stepIdField(), requiredConstStepKindField(kind), requiredPostingIdField()));
      case LIST_POSTINGS ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  stepIdField(),
                  requiredConstStepKindField(kind),
                  optionalQueryField(listPostingsQuerySchema())));
      case ACCOUNT_BALANCE ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan step `" + kind.wireValue() + "`.",
              List.of(
                  stepIdField(),
                  requiredConstStepKindField(kind),
                  requiredQueryField(accountBalanceQuerySchema())));
      case ASSERT ->
          MachineContractSchemaSupport.objectSchema(
              "Ledger-plan assertion step.",
              List.of(stepIdField(), requiredConstStepKindField(kind), requiredAssertionField()));
    };
  }

  private static Map<String, Object> listAccountsQuerySchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional "
            + MachineContractSchemaSupport.operation(OperationId.LIST_ACCOUNTS)
            + " query window.",
        List.of(optionalLimitField(), optionalCursorField()));
  }

  private static Map<String, Object> listPostingsQuerySchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Optional posting-page filter and continuation window.",
        List.of(
            optionalAccountCodeQueryField(),
            optionalEffectiveDateFromQueryField(),
            optionalEffectiveDateToQueryField(),
            optionalLimitField(),
            optionalCursorField()));
  }

  private static Map<String, Object> accountBalanceQuerySchema() {
    return MachineContractSchemaSupport.objectSchema(
        MachineContractSchemaSupport.operation(OperationId.ACCOUNT_BALANCE) + " query payload.",
        List.of(
            requiredAccountCodeQueryField(),
            optionalEffectiveDateFromQueryField(),
            optionalEffectiveDateToQueryField()));
  }

  private static Map<String, Object> assertionSchema() {
    return MachineContractSchemaSupport.oneOfSchema(
        "Assertion payload nested inside an assert step.",
        Arrays.stream(LedgerAssertionKind.values())
            .map(MachineContractLedgerPlanSchemas::assertionVariantSchema)
            .toList());
  }

  private static Map<String, Object> assertionVariantSchema(LedgerAssertionKind kind) {
    return switch (kind) {
      case ACCOUNT_DECLARED ->
          MachineContractSchemaSupport.objectSchema(
              "Assertion `" + kind.wireValue() + "`.",
              List.of(requiredConstAssertionKindField(kind), requiredAssertionAccountCodeField()));
      case ACCOUNT_ACTIVE ->
          MachineContractSchemaSupport.objectSchema(
              "Assertion `" + kind.wireValue() + "`.",
              List.of(requiredConstAssertionKindField(kind), requiredAssertionAccountCodeField()));
      case POSTING_EXISTS ->
          MachineContractSchemaSupport.objectSchema(
              "Assertion `" + kind.wireValue() + "`.",
              List.of(requiredConstAssertionKindField(kind), requiredAssertionPostingIdField()));
      case ACCOUNT_BALANCE_EQUALS ->
          MachineContractSchemaSupport.objectSchema(
              "Assertion `" + kind.wireValue() + "`.",
              List.of(
                  requiredConstAssertionKindField(kind),
                  requiredAssertionAccountCodeField(),
                  optionalAssertionEffectiveDateFromField(),
                  optionalAssertionEffectiveDateToField(),
                  requiredAssertionCurrencyCodeField(),
                  requiredAssertionNetAmountField(),
                  requiredAssertionBalanceSideField()));
    };
  }

  private static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(planIdField(), stepsField());
  }

  private static List<MachineContractFieldSpec> stepFields() {
    return List.of(
        stepIdField(),
        genericStepKindField(),
        conditionalPostingField(),
        conditionalDeclareAccountField(),
        conditionalQueryField(),
        conditionalAssertionField(),
        conditionalPostingIdField());
  }

  private static List<MachineContractFieldSpec> queryFields() {
    return List.of(
        conditionalAccountCodeQueryField(),
        conditionalEffectiveDateFromQueryField(),
        conditionalEffectiveDateToQueryField(),
        conditionalLimitField(),
        conditionalCursorField());
  }

  private static List<MachineContractFieldSpec> assertionFields() {
    return List.of(
        genericAssertionKindField(),
        conditionalAssertionAccountCodeField(),
        conditionalAssertionPostingIdField(),
        conditionalAssertionEffectiveDateFromField(),
        conditionalAssertionEffectiveDateToField(),
        conditionalAssertionCurrencyCodeField(),
        conditionalAssertionNetAmountField(),
        conditionalAssertionBalanceSideField());
  }

  private static List<LedgerStepKind> stepKinds(StepRole role) {
    return Arrays.stream(LedgerStepKind.values()).filter(kind -> stepRole(kind) == role).toList();
  }

  private static StepRole stepRole(LedgerStepKind kind) {
    return switch (kind) {
      case OPEN_BOOK, DECLARE_ACCOUNT -> StepRole.ADMINISTRATION;
      case INSPECT_BOOK, LIST_ACCOUNTS, GET_POSTING, LIST_POSTINGS, ACCOUNT_BALANCE ->
          StepRole.QUERY;
      case PREFLIGHT_ENTRY, POST_ENTRY -> StepRole.WRITE;
      case ASSERT -> StepRole.ASSERT;
    };
  }

  private static MachineContractFieldSpec planIdField() {
    String description = "Caller-supplied plan identifier.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Plan.PLAN_ID,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  private static MachineContractFieldSpec stepsField() {
    String description = "Ordered non-empty array of executable ledger-plan steps.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Plan.STEPS,
        description,
        MachineContractSchemaSupport.arraySchema(
            description, stepSchema(), 1, ProtocolLimits.LEDGER_PLAN_STEP_MAX));
  }

  private static MachineContractFieldSpec stepIdField() {
    String description = "Caller-supplied step identifier unique inside the plan.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Step.STEP_ID,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  private static MachineContractFieldSpec genericStepKindField() {
    String description = "Canonical ledger-plan step kind for this step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Step.KIND,
        description,
        MachineContractSchemaSupport.enumStringSchema(description, LedgerStepKind.wireValues()));
  }

  private static MachineContractFieldSpec requiredConstStepKindField(LedgerStepKind kind) {
    String description = "Canonical ledger-plan step kind for this step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Step.KIND,
        description,
        MachineContractSchemaSupport.constSchema(kind.wireValue(), description));
  }

  private static MachineContractFieldSpec conditionalPostingField() {
    String description =
        "Posting request payload for "
            + operation(OperationId.POST_ENTRY)
            + " and "
            + operation(OperationId.PREFLIGHT_ENTRY)
            + " steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.POSTING,
        description,
        MachineContractPostEntrySchemas.postEntrySchemaWithoutDialect());
  }

  private static MachineContractFieldSpec requiredPostingField() {
    return MachineContractFieldSpec.required(
        conditionalPostingField().name(),
        conditionalPostingField().description(),
        acceptedSchema(conditionalPostingField()));
  }

  private static MachineContractFieldSpec conditionalDeclareAccountField() {
    String description =
        "Account declaration payload for " + operation(OperationId.DECLARE_ACCOUNT) + " steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT,
        description,
        MachineContractDeclareAccountSchemas.declareAccountSchemaWithoutDialect());
  }

  private static MachineContractFieldSpec requiredDeclareAccountField() {
    return MachineContractFieldSpec.required(
        conditionalDeclareAccountField().name(),
        conditionalDeclareAccountField().description(),
        acceptedSchema(conditionalDeclareAccountField()));
  }

  private static MachineContractFieldSpec conditionalQueryField() {
    String description =
        "Query payload for list steps and " + operation(OperationId.ACCOUNT_BALANCE) + " steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.QUERY, description, accountBalanceQuerySchema());
  }

  private static MachineContractFieldSpec optionalQueryField(Map<String, Object> querySchema) {
    return MachineContractFieldSpec.optional(
        conditionalQueryField().name(), conditionalQueryField().description(), querySchema);
  }

  private static MachineContractFieldSpec requiredQueryField(Map<String, Object> querySchema) {
    return MachineContractFieldSpec.required(
        conditionalQueryField().name(), conditionalQueryField().description(), querySchema);
  }

  private static MachineContractFieldSpec conditionalAssertionField() {
    String description = "Assertion payload for first-class assertion steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.ASSERTION, description, assertionSchema());
  }

  private static MachineContractFieldSpec requiredAssertionField() {
    return MachineContractFieldSpec.required(
        conditionalAssertionField().name(),
        conditionalAssertionField().description(),
        acceptedSchema(conditionalAssertionField()));
  }

  private static MachineContractFieldSpec conditionalPostingIdField() {
    String description = "Posting identifier for " + operation(OperationId.GET_POSTING) + " steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Step.POSTING_ID,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  private static MachineContractFieldSpec requiredPostingIdField() {
    return MachineContractFieldSpec.required(
        conditionalPostingIdField().name(),
        conditionalPostingIdField().description(),
        acceptedSchema(conditionalPostingIdField()));
  }

  private static MachineContractFieldSpec conditionalAccountCodeQueryField() {
    String description =
        "Account filter for "
            + operation(OperationId.LIST_POSTINGS)
            + " or required account target for "
            + operation(OperationId.ACCOUNT_BALANCE)
            + ".";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  private static MachineContractFieldSpec optionalAccountCodeQueryField() {
    return MachineContractFieldSpec.optional(
        conditionalAccountCodeQueryField().name(),
        conditionalAccountCodeQueryField().description(),
        acceptedSchema(conditionalAccountCodeQueryField()));
  }

  private static MachineContractFieldSpec requiredAccountCodeQueryField() {
    return MachineContractFieldSpec.required(
        conditionalAccountCodeQueryField().name(),
        conditionalAccountCodeQueryField().description(),
        acceptedSchema(conditionalAccountCodeQueryField()));
  }

  private static MachineContractFieldSpec conditionalEffectiveDateFromQueryField() {
    String description = "Inclusive ISO-8601 effective-date lower bound.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
        description,
        MachineContractSchemaSupport.dateStringSchema(description));
  }

  private static MachineContractFieldSpec optionalEffectiveDateFromQueryField() {
    return MachineContractFieldSpec.optional(
        conditionalEffectiveDateFromQueryField().name(),
        conditionalEffectiveDateFromQueryField().description(),
        acceptedSchema(conditionalEffectiveDateFromQueryField()));
  }

  private static MachineContractFieldSpec conditionalEffectiveDateToQueryField() {
    String description = "Inclusive ISO-8601 effective-date upper bound.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
        description,
        MachineContractSchemaSupport.dateStringSchema(description));
  }

  private static MachineContractFieldSpec optionalEffectiveDateToQueryField() {
    return MachineContractFieldSpec.optional(
        conditionalEffectiveDateToQueryField().name(),
        conditionalEffectiveDateToQueryField().description(),
        acceptedSchema(conditionalEffectiveDateToQueryField()));
  }

  private static MachineContractFieldSpec conditionalLimitField() {
    String description = "Page size for list steps.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.LIMIT,
        description,
        MachineContractSchemaSupport.pageLimitSchema());
  }

  private static MachineContractFieldSpec optionalLimitField() {
    return MachineContractFieldSpec.optional(
        conditionalLimitField().name(),
        conditionalLimitField().description(),
        acceptedSchema(conditionalLimitField()));
  }

  private static MachineContractFieldSpec conditionalCursorField() {
    String description =
        "Opaque cursor for continuing "
            + operation(OperationId.LIST_POSTINGS)
            + " or "
            + operation(OperationId.LIST_ACCOUNTS)
            + " from the prior page.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Query.CURSOR,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  private static MachineContractFieldSpec optionalCursorField() {
    return MachineContractFieldSpec.optional(
        conditionalCursorField().name(),
        conditionalCursorField().description(),
        acceptedSchema(conditionalCursorField()));
  }

  private static MachineContractFieldSpec genericAssertionKindField() {
    String description = "Canonical assertion kind nested inside an assert step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Assertion.KIND,
        description,
        MachineContractSchemaSupport.enumStringSchema(
            description, LedgerAssertionKind.wireValues()));
  }

  private static MachineContractFieldSpec requiredConstAssertionKindField(
      LedgerAssertionKind kind) {
    String description = "Canonical assertion kind nested inside an assert step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Assertion.KIND,
        description,
        MachineContractSchemaSupport.constSchema(kind.wireValue(), description));
  }

  private static MachineContractFieldSpec conditionalAssertionAccountCodeField() {
    String description = "Account code consumed by account assertions.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  private static MachineContractFieldSpec requiredAssertionAccountCodeField() {
    return MachineContractFieldSpec.required(
        conditionalAssertionAccountCodeField().name(),
        conditionalAssertionAccountCodeField().description(),
        acceptedSchema(conditionalAssertionAccountCodeField()));
  }

  private static MachineContractFieldSpec conditionalAssertionPostingIdField() {
    String description = "Posting identifier consumed by posting-existence assertions.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Assertion.POSTING_ID,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  private static MachineContractFieldSpec requiredAssertionPostingIdField() {
    return MachineContractFieldSpec.required(
        conditionalAssertionPostingIdField().name(),
        conditionalAssertionPostingIdField().description(),
        acceptedSchema(conditionalAssertionPostingIdField()));
  }

  private static MachineContractFieldSpec conditionalAssertionEffectiveDateFromField() {
    String description = "Inclusive ISO-8601 effective-date lower bound for balance assertions.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM,
        description,
        MachineContractSchemaSupport.dateStringSchema(description));
  }

  private static MachineContractFieldSpec optionalAssertionEffectiveDateFromField() {
    return MachineContractFieldSpec.optional(
        conditionalAssertionEffectiveDateFromField().name(),
        conditionalAssertionEffectiveDateFromField().description(),
        acceptedSchema(conditionalAssertionEffectiveDateFromField()));
  }

  private static MachineContractFieldSpec conditionalAssertionEffectiveDateToField() {
    String description = "Inclusive ISO-8601 effective-date upper bound for balance assertions.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO,
        description,
        MachineContractSchemaSupport.dateStringSchema(description));
  }

  private static MachineContractFieldSpec optionalAssertionEffectiveDateToField() {
    return MachineContractFieldSpec.optional(
        conditionalAssertionEffectiveDateToField().name(),
        conditionalAssertionEffectiveDateToField().description(),
        acceptedSchema(conditionalAssertionEffectiveDateToField()));
  }

  private static MachineContractFieldSpec conditionalAssertionCurrencyCodeField() {
    String description =
        "Currency bucket expected by " + operation(OperationId.ACCOUNT_BALANCE) + " assertions.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Assertion.CURRENCY_CODE,
        description,
        MachineContractSchemaSupport.nonBlankStringSchema(description));
  }

  private static MachineContractFieldSpec requiredAssertionCurrencyCodeField() {
    return MachineContractFieldSpec.required(
        conditionalAssertionCurrencyCodeField().name(),
        conditionalAssertionCurrencyCodeField().description(),
        acceptedSchema(conditionalAssertionCurrencyCodeField()));
  }

  private static MachineContractFieldSpec conditionalAssertionNetAmountField() {
    String description =
        "Plain decimal expected net amount for "
            + operation(OperationId.ACCOUNT_BALANCE)
            + " assertions.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Assertion.NET_AMOUNT,
        description,
        MachineContractSchemaSupport.decimalAmountStringSchema(description));
  }

  private static MachineContractFieldSpec requiredAssertionNetAmountField() {
    return MachineContractFieldSpec.required(
        conditionalAssertionNetAmountField().name(),
        conditionalAssertionNetAmountField().description(),
        acceptedSchema(conditionalAssertionNetAmountField()));
  }

  private static MachineContractFieldSpec conditionalAssertionBalanceSideField() {
    String description =
        "Expected normal-balance side for "
            + operation(OperationId.ACCOUNT_BALANCE)
            + " assertions.";
    return MachineContractFieldSpec.conditional(
        ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE,
        description,
        MachineContractSchemaSupport.enumStringSchema(description, BalanceSide.wireValues()));
  }

  private static MachineContractFieldSpec requiredAssertionBalanceSideField() {
    return MachineContractFieldSpec.required(
        conditionalAssertionBalanceSideField().name(),
        conditionalAssertionBalanceSideField().description(),
        acceptedSchema(conditionalAssertionBalanceSideField()));
  }

  private static String operation(OperationId operationId) {
    return MachineContractSchemaSupport.operation(operationId);
  }

  private static Map<String, Object> acceptedSchema(MachineContractFieldSpec field) {
    return Objects.requireNonNull(field.acceptedSchema(), field.name());
  }
}
