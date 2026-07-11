package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.PostingInventoryRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Set;

/** Executor-local entry-mode violations derived from canonical contract rejections. */
public final class BookkeepingEntryModeSemanticsViolations {
  private BookkeepingEntryModeSemanticsViolations() {}

  /** Creates one economic-null-journal violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation economicNullJournal(
      String selectorField, String selectorValue) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.economicNullJournal(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue)));
  }

  /** Creates one basis-gated receivable-side verb violation. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation verbRequiresReceivableRole(
      String selectorField, String selectorValue) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.verbRequiresRole(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            AccountRole.RECEIVABLE));
  }

  /** Creates one basis-gated payable-side verb violation. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation verbRequiresPayableRole(
      String selectorField, String selectorValue) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.verbRequiresRole(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            AccountRole.PAYABLE));
  }

  /** Creates one raw-journal shadowing violation for a direct journal. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation rawJournalShadowsTypedEvent(
      String selectorField,
      String selectorValue,
      EconomicEventClass eventClass,
      String operationName) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.rawJournalShadowsTypedEvent(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            eventClass,
            operationName));
  }

  /** Creates one compound-operational raw-journal violation. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      rawJournalBundlesOperationalEvents(
          String selectorField,
          String selectorValue,
          Set<EconomicEventClass> containedTypedEvents) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.rawJournalBundlesOperationalEvents(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            containedTypedEvents));
  }

  /** Creates one cash-line requirement violation for a raw journal on a cash-basis book. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation rawJournalRequiresCashLine(
      String selectorField, String selectorValue) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.rawJournalRequiresCashLine(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue)));
  }

  /** Creates one opening-window nominal-account violation. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      openingWindowAccountNotPermitted(
          String selectorField, String selectorValue, AccountCode accountCode) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.openingWindowAccountNotPermitted(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            accountCode));
  }

  /** Creates one refusal when capitalization would create a cost-only inventory pool. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      inventoryCapitalizationRequiresQuantityOnHand(AccountCode accountCode) {
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingInventoryRejectionSemantics.inventoryCapitalizationRequiresQuantityOnHand(
            accountCode));
  }

  /** Creates one refusal when an opening inventory movement is not the account's first movement. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      inventoryOpeningMustBeFirstMovement(AccountCode accountCode) {
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingInventoryRejectionSemantics.inventoryOpeningMustBeFirstMovement(accountCode));
  }

  /** Creates one refusal when an inventory opening balance cannot establish an exact pool. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      inventoryOpeningCarryingCostInvalid(AccountCode accountCode) {
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingInventoryRejectionSemantics.inventoryOpeningCarryingCostInvalid(accountCode));
  }
}
