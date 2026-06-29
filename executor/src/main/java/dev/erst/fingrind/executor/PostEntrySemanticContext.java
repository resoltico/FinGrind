package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEntrySemanticsViolationFactory;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Derived semantic routing facts for one published post-entry request. */
record PostEntrySemanticContext(
    BookkeepingEntryKind entryKind,
    RequestSurfaceFacts.SourceDocumentTypeFacts sourceDocumentTypes,
    Set<AccountCode> referencedAccounts) {
  PostEntrySemanticContext {
    Objects.requireNonNull(entryKind, "entryKind");
    Objects.requireNonNull(sourceDocumentTypes, "sourceDocumentTypes");
    referencedAccounts =
        Set.copyOf(Objects.requireNonNull(referencedAccounts, "referencedAccounts"));
  }

  static PostEntrySemanticContext from(
      BookkeepingEntry entry, RequestSurfaceFacts requestSurfaceFacts) {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(requestSurfaceFacts, "requestSurfaceFacts");
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        requestSurfaceFacts.bookkeepingEntryKind(entry.entryKind());
    return new PostEntrySemanticContext(
        entry.entryKind(), entryKindFacts.sourceDocumentTypes(), referencedAccounts(entry));
  }

  String selectorField() {
    return "entryKind";
  }

  String selectorValue() {
    return entryKind.wireValue();
  }

  private static Set<AccountCode> referencedAccounts(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.DirectJournal journal ->
          journal.lines().stream()
              .map(dev.erst.fingrind.core.JournalLine::accountCode)
              .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
      case BookkeepingEntry.Sale sale ->
          BookkeepingEntrySemanticsViolationFactory.referencedAccountSet(
              sale.cashAccountCode(), sale.revenueAccountCode());
      case BookkeepingEntry.Expense expense ->
          BookkeepingEntrySemanticsViolationFactory.referencedAccountSet(
              expense.expenseAccountCode(), expense.cashAccountCode());
      case BookkeepingEntry.OwnerContribution contribution ->
          BookkeepingEntrySemanticsViolationFactory.referencedAccountSet(
              contribution.cashAccountCode(), contribution.equityAccountCode());
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          BookkeepingEntrySemanticsViolationFactory.referencedAccountSet(
              withdrawal.equityAccountCode(), withdrawal.cashAccountCode());
      case BookkeepingEntry.OpeningPosition openingPosition ->
          openingPosition.balances().stream()
              .map(BookkeepingEntry.OpeningPosition.OpeningAccountBalance::accountCode)
              .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
      case BookkeepingEntry.Reversal reversal ->
          reversal.lines().stream()
              .map(dev.erst.fingrind.core.JournalLine::accountCode)
              .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    };
  }
}
