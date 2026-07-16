package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.StandardBookkeepingEntryVariants;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Collections;
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
        Collections.unmodifiableSet(
            new LinkedHashSet<>(Objects.requireNonNull(referencedAccounts, "referencedAccounts")));
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
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          PostEntryInventorySemanticContext.referencedAccounts(inventoryEntry);
      case AccrualCutoffBookkeepingEntryVariants accrualCutoffEntry ->
          PostEntryAccrualCutoffSemanticContext.referencedAccounts(accrualCutoffEntry);
      case LatvianPayrollBookkeepingEntryVariants payrollEntry ->
          PostEntryLatvianPayrollSemanticContext.referencedAccounts(payrollEntry);
      case FixedAssetBookkeepingEntryVariants fixedAssetEntry ->
          PostEntryFixedAssetSemanticContext.referencedAccounts(fixedAssetEntry);
      case FinancingBookkeepingEntryVariants financingEntry ->
          PostEntryFinancingSemanticContext.referencedAccounts(financingEntry);
      case RealizedForeignExchangeBookkeepingEntryVariants realizedForeignExchangeEntry ->
          PostEntryRealizedForeignExchangeSemanticContext.referencedAccounts(
              realizedForeignExchangeEntry);
      case StandardBookkeepingEntryVariants standardEntry ->
          PostEntryStandardSemanticContext.referencedAccounts(standardEntry);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          openingPosition.balances().stream()
              .map(BookkeepingEntry.OpeningPosition.OpeningAccountBalance::accountCode)
              .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
      case BookkeepingEntry.Reversal reversal ->
          reversal.resolvedJournalEntry() == null
              ? Set.of()
              : reversal.lines().stream()
                  .map(dev.erst.fingrind.core.JournalLine::accountCode)
                  .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    };
  }
}
