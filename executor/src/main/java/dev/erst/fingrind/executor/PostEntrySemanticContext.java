package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAccountSemanticsViolations;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

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
      case BookkeepingEntry.SaleSettled sale ->
          referencedAccountSet(
              sale.cashAccountCode(),
              sale.revenueAccountCode(),
              taxAccountCode(sale.appliedTax()),
              inventoryReliefAccountCode(sale.inventoryRelief(), true),
              inventoryReliefAccountCode(sale.inventoryRelief(), false));
      case BookkeepingEntry.SaleOnCredit sale ->
          referencedAccountSet(
              sale.receivableAccountCode(),
              sale.revenueAccountCode(),
              taxAccountCode(sale.appliedTax()),
              inventoryReliefAccountCode(sale.inventoryRelief(), true),
              inventoryReliefAccountCode(sale.inventoryRelief(), false));
      case BookkeepingEntry.PurchaseSettled purchase ->
          referencedAccountSet(
              purchase.inventoryAccountCode(),
              purchase.cashAccountCode(),
              taxAccountCode(purchase.appliedTax()));
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          referencedAccountSet(
              purchase.inventoryAccountCode(),
              purchase.payableAccountCode(),
              taxAccountCode(purchase.appliedTax()));
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          PostEntryInventorySemanticContext.referencedAccounts(inventoryEntry);
      case BookkeepingEntry.ExpenseSettled expense ->
          referencedAccountSet(
              expense.expenseAccountCode(),
              expense.cashAccountCode(),
              taxAccountCode(expense.appliedTax()));
      case BookkeepingEntry.ExpenseOnCredit expense ->
          referencedAccountSet(
              expense.expenseAccountCode(),
              expense.payableAccountCode(),
              taxAccountCode(expense.appliedTax()));
      case BookkeepingEntry.Receipt receipt ->
          referencedAccountSet(
              receipt.cashAccountCode(),
              receipt.receivableAccountCode(),
              receipt.settlementAdjunct() == null
                  ? null
                  : receipt.settlementAdjunct().accountCode());
      case BookkeepingEntry.Payment payment ->
          referencedAccountSet(
              payment.payableAccountCode(),
              payment.cashAccountCode(),
              payment.settlementAdjunct() == null
                  ? null
                  : payment.settlementAdjunct().accountCode());
      case BookkeepingEntry.OwnerContribution contribution ->
          BookkeepingAccountSemanticsViolations.referencedAccountSet(
              contribution.cashAccountCode(), contribution.equityAccountCode());
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          BookkeepingAccountSemanticsViolations.referencedAccountSet(
              withdrawal.equityAccountCode(), withdrawal.cashAccountCode());
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

  private static Set<AccountCode> referencedAccountSet(
      AccountCode firstAccountCode,
      AccountCode secondAccountCode,
      @Nullable AccountCode thirdAccountCode) {
    return referencedAccountSet(firstAccountCode, secondAccountCode, thirdAccountCode, null, null);
  }

  private static Set<AccountCode> referencedAccountSet(
      AccountCode firstAccountCode,
      AccountCode secondAccountCode,
      @Nullable AccountCode thirdAccountCode,
      @Nullable AccountCode fourthAccountCode,
      @Nullable AccountCode fifthAccountCode) {
    Set<AccountCode> referencedAccounts = new LinkedHashSet<>();
    referencedAccounts.add(firstAccountCode);
    referencedAccounts.add(secondAccountCode);
    if (thirdAccountCode != null) {
      referencedAccounts.add(thirdAccountCode);
    }
    if (fourthAccountCode != null) {
      referencedAccounts.add(fourthAccountCode);
    }
    if (fifthAccountCode != null) {
      referencedAccounts.add(fifthAccountCode);
    }
    return referencedAccounts;
  }

  private static @Nullable AccountCode taxAccountCode(@Nullable AppliedTax appliedTax) {
    return appliedTax == null ? null : appliedTax.taxAccountCode();
  }

  private static @Nullable AccountCode inventoryReliefAccountCode(
      dev.erst.fingrind.contract.bookkeeping.@org.jspecify.annotations.Nullable InventoryRelief
          inventoryRelief,
      boolean inventoryAccount) {
    if (inventoryRelief == null) {
      return null;
    }
    return inventoryAccount
        ? inventoryRelief.inventoryAccountCode()
        : inventoryRelief.costOfSalesAccountCode();
  }
}
