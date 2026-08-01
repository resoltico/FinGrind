package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.util.EnumMap;
import java.util.Map;

/** Canonical template-shape policy tables for ledger-plan steps and assertions. */
final class ContractTemplateShapeRules {
  private static final ContractTemplateStepShapeRequirements INSPECT_BOOK_STEP_SHAPE =
      stepShape(
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          false);
  private static final ContractTemplateStepShapeRequirements DECLARE_ACCOUNT_STEP_SHAPE =
      stepShape(
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.REQUIRED,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          false);
  private static final ContractTemplateStepShapeRequirements DECLARE_TAX_REGISTRATION_STEP_SHAPE =
      stepShape(
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.REQUIRED,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          false);
  private static final ContractTemplateStepShapeRequirements POSTING_STEP_SHAPE =
      stepShape(
          ContractTemplateFieldPresence.REQUIRED,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          false);
  private static final ContractTemplateStepShapeRequirements OPTIONAL_QUERY_STEP_SHAPE =
      stepShape(
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.OPTIONAL,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          false);
  private static final ContractTemplateStepShapeRequirements REQUIRED_BALANCE_QUERY_STEP_SHAPE =
      stepShape(
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.REQUIRED,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          true);
  private static final ContractTemplateStepShapeRequirements POSTING_ID_STEP_SHAPE =
      stepShape(
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.REQUIRED,
          false);
  private static final ContractTemplateStepShapeRequirements ASSERTION_STEP_SHAPE =
      stepShape(
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.REQUIRED,
          ContractTemplateFieldPresence.FORBIDDEN,
          false);
  private static final ContractTemplateAssertionShapeRequirements ACCOUNT_CODE_ASSERTION_SHAPE =
      assertionShape(
          ContractTemplateFieldPresence.REQUIRED,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN);
  private static final ContractTemplateAssertionShapeRequirements POSTING_ID_ASSERTION_SHAPE =
      assertionShape(
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.REQUIRED);
  private static final ContractTemplateAssertionShapeRequirements ACCOUNT_BALANCE_ASSERTION_SHAPE =
      assertionShape(
          ContractTemplateFieldPresence.REQUIRED,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.FORBIDDEN,
          ContractTemplateFieldPresence.REQUIRED,
          ContractTemplateFieldPresence.REQUIRED,
          ContractTemplateFieldPresence.FORBIDDEN);

  private ContractTemplateShapeRules() {}

  static Map<LedgerStepKind, ContractTemplateStepShapeRequirements> stepShapeRequirements() {
    var requirements =
        new EnumMap<LedgerStepKind, ContractTemplateStepShapeRequirements>(LedgerStepKind.class);
    for (LedgerStepKind kind : LedgerStepKind.values()) {
      if (kind.commitsPosting()) {
        requirements.put(kind, POSTING_STEP_SHAPE);
      }
    }
    requirements.put(LedgerStepKind.INSPECT_BOOK, INSPECT_BOOK_STEP_SHAPE);
    requirements.put(LedgerStepKind.DECLARE_ACCOUNT, DECLARE_ACCOUNT_STEP_SHAPE);
    requirements.put(LedgerStepKind.DECLARE_TAX_REGISTRATION, DECLARE_TAX_REGISTRATION_STEP_SHAPE);
    requirements.put(LedgerStepKind.PREFLIGHT_ENTRY, POSTING_STEP_SHAPE);
    requirements.put(LedgerStepKind.LIST_ACCOUNTS, OPTIONAL_QUERY_STEP_SHAPE);
    requirements.put(LedgerStepKind.LIST_POSTINGS, OPTIONAL_QUERY_STEP_SHAPE);
    requirements.put(LedgerStepKind.ACCOUNT_BALANCE, REQUIRED_BALANCE_QUERY_STEP_SHAPE);
    requirements.put(LedgerStepKind.GET_POSTING, POSTING_ID_STEP_SHAPE);
    requirements.put(LedgerStepKind.ASSERT, ASSERTION_STEP_SHAPE);
    return Map.copyOf(requirements);
  }

  static Map<LedgerAssertionKind, ContractTemplateAssertionShapeRequirements>
      assertionShapeRequirements() {
    return Map.ofEntries(
        Map.entry(LedgerAssertionKind.ACCOUNT_DECLARED, ACCOUNT_CODE_ASSERTION_SHAPE),
        Map.entry(LedgerAssertionKind.ACCOUNT_ACTIVE, ACCOUNT_CODE_ASSERTION_SHAPE),
        Map.entry(LedgerAssertionKind.POSTING_EXISTS, POSTING_ID_ASSERTION_SHAPE),
        Map.entry(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS, ACCOUNT_BALANCE_ASSERTION_SHAPE));
  }

  private static ContractTemplateStepShapeRequirements stepShape(
      ContractTemplateFieldPresence posting,
      ContractTemplateFieldPresence declareAccount,
      ContractTemplateFieldPresence declareTaxRegistration,
      ContractTemplateFieldPresence query,
      ContractTemplateFieldPresence assertion,
      ContractTemplateFieldPresence postingId,
      boolean queryAccountCodeRequired) {
    return new ContractTemplateStepShapeRequirements(
        posting,
        declareAccount,
        declareTaxRegistration,
        query,
        assertion,
        postingId,
        queryAccountCodeRequired);
  }

  private static ContractTemplateAssertionShapeRequirements assertionShape(
      ContractTemplateFieldPresence accountCode,
      ContractTemplateFieldPresence effectiveDateFrom,
      ContractTemplateFieldPresence effectiveDateTo,
      ContractTemplateFieldPresence netAmount,
      ContractTemplateFieldPresence balanceSide,
      ContractTemplateFieldPresence postingId) {
    return new ContractTemplateAssertionShapeRequirements(
        accountCode, effectiveDateFrom, effectiveDateTo, netAmount, balanceSide, postingId);
  }
}
