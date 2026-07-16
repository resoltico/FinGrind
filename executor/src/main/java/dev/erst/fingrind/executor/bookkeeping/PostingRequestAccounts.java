package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.StandardBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Extracts canonical requested-account sets from posting requests. */
final class PostingRequestAccounts {
  private PostingRequestAccounts() {}

  static Set<AccountCode> requestedAccounts(PostingRequestModel postingRequest) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    if (postingRequest.resolvedOriginatingEntry().isPresent()) {
      return requestedAccounts(postingRequest.resolvedOriginatingEntry().orElseThrow());
    }
    if (postingRequest.callerAuthoredEntry().isPresent()) {
      return requestedAccounts(postingRequest.callerAuthoredEntry().orElseThrow());
    }
    Set<AccountCode> requestedAccounts = new LinkedHashSet<>();
    for (JournalLine line : postingRequest.journalEntry().lines()) {
      requestedAccounts.add(line.accountCode());
    }
    return immutableOrderedSet(requestedAccounts);
  }

  private static Set<AccountCode> requestedAccounts(BookkeepingEntry entry) {
    Set<AccountCode> requestedAccounts = new LinkedHashSet<>();
    switch (entry) {
      case BookkeepingEntry.DirectJournal journal ->
          journal.lines().stream().map(JournalLine::accountCode).forEach(requestedAccounts::add);
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          PostingRequestInventoryAccounts.add(requestedAccounts, inventoryEntry);
      case AccrualCutoffBookkeepingEntryVariants accrualCutoffEntry ->
          PostingRequestAccrualCutoffAccounts.add(requestedAccounts, accrualCutoffEntry);
      case dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants
              payrollEntry ->
          PostingRequestLatvianPayrollAccounts.add(requestedAccounts, payrollEntry);
      case FixedAssetBookkeepingEntryVariants fixedAssetEntry ->
          PostingRequestFixedAssetAccounts.add(requestedAccounts, fixedAssetEntry);
      case FinancingBookkeepingEntryVariants financingEntry ->
          PostingRequestFinancingAccounts.add(requestedAccounts, financingEntry);
      case RealizedForeignExchangeBookkeepingEntryVariants realizedForeignExchangeEntry ->
          PostingRequestRealizedForeignExchangeAccounts.add(
              requestedAccounts, realizedForeignExchangeEntry);
      case StandardBookkeepingEntryVariants standardEntry ->
          PostingRequestStandardTypedEntryAccounts.add(requestedAccounts, standardEntry);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          openingPosition.balances().stream()
              .map(BookkeepingEntry.OpeningPosition.OpeningAccountBalance::accountCode)
              .forEach(requestedAccounts::add);
      case BookkeepingEntry.Reversal reversal -> {
        if (reversal.resolvedJournalEntry() != null) {
          reversal.lines().stream().map(JournalLine::accountCode).forEach(requestedAccounts::add);
        }
      }
    }
    return immutableOrderedSet(requestedAccounts);
  }

  private static Set<AccountCode> immutableOrderedSet(Set<AccountCode> requestedAccounts) {
    return Collections.unmodifiableSet(new LinkedHashSet<>(requestedAccounts));
  }
}
