package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.BalanceSide;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Validates the structural shape of contract-owned request and ledger-plan templates. */
final class ContractTemplateShapeValidator {
  private static final Map<LedgerStepKind, ContractTemplateStepShapeRequirements>
      STEP_SHAPE_REQUIREMENTS = ContractTemplateShapeRules.stepShapeRequirements();
  private static final Map<LedgerAssertionKind, ContractTemplateAssertionShapeRequirements>
      ASSERTION_SHAPE_REQUIREMENTS = ContractTemplateShapeRules.assertionShapeRequirements();

  private ContractTemplateShapeValidator() {}

  static void validateStepShape(
      LedgerStepKind kind,
      ContractTemplates.@Nullable PostingRequestTemplateDescriptor posting,
      ContractTemplates.@Nullable DeclareAccountTemplateDescriptor declareAccount,
      ContractTemplates.@Nullable LedgerPlanQueryTemplateDescriptor query,
      ContractTemplates.@Nullable LedgerAssertionTemplateDescriptor assertion,
      @Nullable String postingId) {
    ContractTemplateStepShapeRequirements requirements =
        Objects.requireNonNull(STEP_SHAPE_REQUIREMENTS.get(kind), kind.toString());
    requirePresence(kind, "posting", posting, requirements.posting());
    requirePresence(kind, "declareAccount", declareAccount, requirements.declareAccount());
    requirePresence(kind, "query", query, requirements.query());
    requirePresence(kind, "assertion", assertion, requirements.assertion());
    requirePresence(kind, "postingId", postingId, requirements.postingId());
    if (requirements.queryAccountCodeRequired()) {
      ContractTemplates.LedgerPlanQueryTemplateDescriptor accountBalanceQuery =
          Objects.requireNonNull(query, "query");
      if (accountBalanceQuery.accountCode() == null) {
        throw new IllegalArgumentException(
            "query.accountCode is required for account balance template steps.");
      }
    }
  }

  static void validateAssertionShape(
      LedgerAssertionKind kind,
      @Nullable String accountCode,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      @Nullable String currencyCode,
      @Nullable String netAmount,
      @Nullable BalanceSide balanceSide,
      @Nullable String postingId) {
    ContractTemplateAssertionShapeRequirements requirements =
        Objects.requireNonNull(ASSERTION_SHAPE_REQUIREMENTS.get(kind), kind.toString());
    requirePresence(kind, "accountCode", accountCode, requirements.accountCode());
    requirePresence(kind, "effectiveDateFrom", effectiveDateFrom, requirements.effectiveDateFrom());
    requirePresence(kind, "effectiveDateTo", effectiveDateTo, requirements.effectiveDateTo());
    requirePresence(kind, "currencyCode", currencyCode, requirements.currencyCode());
    requirePresence(kind, "netAmount", netAmount, requirements.netAmount());
    requirePresence(kind, "balanceSide", balanceSide, requirements.balanceSide());
    requirePresence(kind, "postingId", postingId, requirements.postingId());
  }

  private static void requirePresence(
      Enum<?> owner,
      String fieldName,
      @Nullable Object value,
      ContractTemplateFieldPresence presence) {
    if (presence == ContractTemplateFieldPresence.REQUIRED && value == null) {
      throw new IllegalArgumentException(
          fieldName + " is required for " + owner + " template shapes.");
    }
    if (presence == ContractTemplateFieldPresence.FORBIDDEN && value != null) {
      throw new IllegalArgumentException(
          fieldName + " must be absent for " + owner + " template shapes.");
    }
  }
}
