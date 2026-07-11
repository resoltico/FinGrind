package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
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
      case BookkeepingEntry.SaleSettled sale -> {
        requestedAccounts.add(sale.cashAccountCode());
        requestedAccounts.add(sale.revenueAccountCode());
        if (sale.inventoryRelief() != null) {
          requestedAccounts.add(sale.inventoryRelief().inventoryAccountCode());
          requestedAccounts.add(sale.inventoryRelief().costOfSalesAccountCode());
        }
      }
      case BookkeepingEntry.SaleOnCredit sale -> {
        requestedAccounts.add(sale.receivableAccountCode());
        requestedAccounts.add(sale.revenueAccountCode());
        if (sale.inventoryRelief() != null) {
          requestedAccounts.add(sale.inventoryRelief().inventoryAccountCode());
          requestedAccounts.add(sale.inventoryRelief().costOfSalesAccountCode());
        }
      }
      case BookkeepingEntry.PurchaseSettled purchase -> {
        requestedAccounts.add(purchase.inventoryAccountCode());
        requestedAccounts.add(purchase.cashAccountCode());
      }
      case BookkeepingEntry.PurchaseOnCredit purchase -> {
        requestedAccounts.add(purchase.inventoryAccountCode());
        requestedAccounts.add(purchase.payableAccountCode());
      }
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          PostingRequestInventoryAccounts.add(requestedAccounts, inventoryEntry);
      case BookkeepingEntry.ExpenseSettled expense -> {
        requestedAccounts.add(expense.expenseAccountCode());
        requestedAccounts.add(expense.cashAccountCode());
      }
      case BookkeepingEntry.ExpenseOnCredit expense -> {
        requestedAccounts.add(expense.expenseAccountCode());
        requestedAccounts.add(expense.payableAccountCode());
      }
      case BookkeepingEntry.Receipt receipt -> {
        requestedAccounts.add(receipt.cashAccountCode());
        requestedAccounts.add(receipt.receivableAccountCode());
        if (receipt.settlementAdjunct() != null) {
          requestedAccounts.add(receipt.settlementAdjunct().accountCode());
        }
      }
      case BookkeepingEntry.Payment payment -> {
        requestedAccounts.add(payment.payableAccountCode());
        requestedAccounts.add(payment.cashAccountCode());
        if (payment.settlementAdjunct() != null) {
          requestedAccounts.add(payment.settlementAdjunct().accountCode());
        }
      }
      case BookkeepingEntry.OwnerContribution contribution -> {
        requestedAccounts.add(contribution.cashAccountCode());
        requestedAccounts.add(contribution.equityAccountCode());
      }
      case BookkeepingEntry.OwnerWithdrawal withdrawal -> {
        requestedAccounts.add(withdrawal.equityAccountCode());
        requestedAccounts.add(withdrawal.cashAccountCode());
      }
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
