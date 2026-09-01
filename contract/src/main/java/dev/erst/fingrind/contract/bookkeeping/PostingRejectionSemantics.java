package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.SourceDocumentType;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Canonical public namespace for posting entry-semantics rejection factories.
 *
 * <p>The behavior owners already live in narrower account, evidence, and entry-mode helpers.
 * Inventory-only admission boundaries publish through {@link PostingInventoryRejectionSemantics} so
 * this facade can stay focused on the general posting-semantics vocabulary.
 */
public final class PostingRejectionSemantics {
  private PostingRejectionSemantics() {}

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  public static PostingRejection.EntrySemanticsViolation accountTypeMismatch(
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountType expectedAccountType,
      AccountType actualAccountType) {
    return PostingAccountRejectionSemantics.accountTypeMismatch(
        selectorValue, field, accountCode, expectedAccountType, actualAccountType);
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  public static PostingRejection.EntrySemanticsViolation cashFlowAssetClassificationMismatch(
      String selectorValue,
      String field,
      AccountCode accountCode,
      CashFlowAssetClassification expectedClassification,
      @Nullable CashFlowAssetClassification actualClassification) {
    return PostingAccountRejectionSemantics.cashFlowAssetClassificationMismatch(
        selectorValue, field, accountCode, expectedClassification, actualClassification);
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  public static PostingRejection.EntrySemanticsViolation financialPositionClassificationMismatch(
      String selectorValue,
      String field,
      AccountCode accountCode,
      FinancialPositionLineClassification expectedClassification,
      @Nullable FinancialPositionLineClassification actualClassification) {
    return PostingAccountRejectionSemantics.financialPositionClassificationMismatch(
        selectorValue, field, accountCode, expectedClassification, actualClassification);
  }

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  public static PostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      String selectorValue, SourceDocumentType sourceDocumentType, List<String> acceptedTypes) {
    return PostingEvidenceRejectionSemantics.sourceDocumentTypeNotAccepted(
        selectorValue, sourceDocumentType, acceptedTypes);
  }

  /** Returns one refusal for a tax composition that exceeds FinGrind's exact monetary range. */
  public static PostingRejection.EntrySemanticsViolation taxCompositionMoneyRangeExceeded(
      String selectorValue) {
    return PostingRejectionTaxSemantics.taxCompositionMoneyRangeExceeded(selectorValue);
  }

  /** Returns one refusal when a posting would overflow a persisted ledger aggregate. */
  public static PostingRejection.EntrySemanticsViolation ledgerAggregateMoneyRangeExceeded(
      String selectorValue, AccountCode accountCode, String currencyCode) {
    return new PostingRejection.EntrySemanticsViolation(
        "ledger-aggregate-money-range-exceeded",
        "journal-lines",
        "entryKind '%s' would exceed FinGrind's exact ledger aggregate range for account '%s' in currency '%s'."
            .formatted(selectorValue, accountCode.value(), currencyCode));
  }

  /** Returns one entry-semantics violation when two semantic roles collapse onto one account. */
  public static PostingRejection.EntrySemanticsViolation distinctRoleAccountsRequired(
      String selectorValue, String firstField, String secondField, AccountCode accountCode) {
    return PostingAccountRejectionSemantics.distinctRoleAccountsRequired(
        selectorValue, firstField, secondField, accountCode);
  }

  /** Returns one entry-semantics violation for raw journals that net every account to zero. */
  public static PostingRejection.EntrySemanticsViolation economicNullJournal(String selectorValue) {
    return PostingEntryModeRejectionSemantics.economicNullJournal(selectorValue);
  }

  /**
   * Returns one entry-semantics violation for an account whose resolved role contradicts the entry.
   */
  public static PostingRejection.EntrySemanticsViolation accountRoleMismatch(
      String selectorValue,
      String field,
      AccountCode accountCode,
      AccountRole expectedRole,
      AccountRole actualRole) {
    return PostingAccountRejectionSemantics.accountRoleMismatch(
        selectorValue, field, accountCode, expectedRole, actualRole);
  }

  /** Returns one basis-gated typed-event refusal for the required trade-side role. */
  public static PostingRejection.EntrySemanticsViolation verbRequiresRole(
      String selectorValue, AccountRole requiredRole) {
    return PostingEntryModeRejectionSemantics.verbRequiresRole(selectorValue, requiredRole);
  }

  /** Returns one evidence-class conflict between retained evidence and the resolved event class. */
  public static PostingRejection.EntrySemanticsViolation evidenceClassConflict(
      String selectorValue, EvidenceClass evidenceClass, EconomicEventClass eventClass) {
    return PostingEvidenceRejectionSemantics.evidenceClassConflict(
        selectorValue, evidenceClass, eventClass);
  }

  /** Returns one refusal when the raw direct-journal path shadows one typed business event. */
  public static PostingRejection.EntrySemanticsViolation rawJournalShadowsTypedEvent(
      String selectorValue, EconomicEventClass eventClass, String operationName) {
    return PostingEntryModeRejectionSemantics.rawJournalShadowsTypedEvent(
        selectorValue, eventClass, operationName);
  }

  /** Returns one refusal when the raw direct-journal path bundles multiple operational events. */
  public static PostingRejection.EntrySemanticsViolation rawJournalBundlesOperationalEvents(
      String selectorValue, Set<EconomicEventClass> containedTypedEvents) {
    return PostingEntryModeRejectionSemantics.rawJournalBundlesOperationalEvents(
        selectorValue, containedTypedEvents);
  }

  /** Returns one refusal when a cash-basis raw adjustment omits every declared cash line. */
  public static PostingRejection.EntrySemanticsViolation rawJournalRequiresCashLine(
      String selectorValue) {
    return PostingEntryModeRejectionSemantics.rawJournalRequiresCashLine(selectorValue);
  }

  /**
   * Returns one refusal when an opening-position request references a forbidden opening account.
   */
  public static PostingRejection.EntrySemanticsViolation openingWindowAccountNotPermitted(
      String selectorValue, AccountCode accountCode) {
    return PostingEntryModeRejectionSemantics.openingWindowAccountNotPermitted(
        selectorValue, accountCode);
  }
}
