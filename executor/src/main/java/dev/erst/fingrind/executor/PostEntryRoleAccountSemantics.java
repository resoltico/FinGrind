package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntryAccountDistinctness.distinct;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.StandardBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;

/** Event-role account semantics for the published business-event posting surface. */
final class PostEntryRoleAccountSemantics {
  private PostEntryRoleAccountSemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry entry,
      String selectorField,
      String selectorValue) {
    switch (entry) {
      case BookkeepingEntry.DirectJournal journal ->
          DirectJournalEntrySemantics.validate(
              violations, accounts, selectorField, selectorValue, journal.lines());
      case dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants
              inventoryEntry ->
          PostEntryInventoryRoleAccountSemantics.validate(
              violations, accounts, inventoryEntry, selectorField, selectorValue);
      case dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants
              accrualCutoffEntry ->
          PostEntryAccrualCutoffRoleAccountSemantics.validate(
              violations, accounts, accrualCutoffEntry, selectorField, selectorValue);
      case dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants
              payrollEntry ->
          PostEntryLatvianPayrollRoleAccountSemantics.validate(
              violations, accounts, payrollEntry, selectorField, selectorValue);
      case dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants
              fixedAssetEntry ->
          PostEntryFixedAssetRoleAccountSemantics.validate(
              violations, accounts, fixedAssetEntry, selectorField, selectorValue);
      case dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants
              financingEntry ->
          PostEntryFinancingRoleAccountSemantics.validate(
              violations, accounts, financingEntry, selectorField, selectorValue);
      case dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants
              realizedForeignExchangeEntry ->
          PostEntryRealizedForeignExchangeRoleAccountSemantics.validate(
              violations, accounts, realizedForeignExchangeEntry, selectorField, selectorValue);
      case StandardBookkeepingEntryVariants standardEntry ->
          PostEntryStandardRoleAccountSemantics.validate(
              violations, accounts, standardEntry, selectorField, selectorValue);
      case BookkeepingEntry.OpeningPosition _ -> {}
      case BookkeepingEntry.Reversal _ -> {}
    }
  }

  static void validatePair(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      PostEntryAccountExpectation firstExpectation,
      PostEntryAccountExpectation secondExpectation) {
    PostEntryRoleAccountValidationSupport.validate(
        violations,
        accounts,
        selectorField,
        selectorValue,
        distinct(firstExpectation, secondExpectation),
        firstExpectation,
        secondExpectation);
  }
}
