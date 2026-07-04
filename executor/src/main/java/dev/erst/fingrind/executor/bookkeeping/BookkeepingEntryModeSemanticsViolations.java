package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.BookTemplateId;
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

  /** Creates one trading-template admission violation for inventory-purchase verbs. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation verbRequiresTradingTemplate(
      String selectorField, String selectorValue, BookTemplateId bookTemplateId) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.verbRequiresTradingTemplate(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            bookTemplateId));
  }

  /** Creates one trading-sale inventory-relief requirement violation. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      tradingSaleRequiresInventoryRelief(String selectorField, String selectorValue) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.tradingSaleRequiresInventoryRelief(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue)));
  }

  /** Creates one non-trading inventory-relief violation. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      inventoryReliefRequiresTradingBook(
          String selectorField, String selectorValue, BookTemplateId bookTemplateId) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.inventoryReliefRequiresTradingBook(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            bookTemplateId));
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
}
