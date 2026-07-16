package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.StandardBookkeepingEntryVariants;

/** Writes caller-authored typed-entry fingerprint fields other than shared FX details. */
final class RequestFingerprintTypedEntryWriter {
  private RequestFingerprintTypedEntryWriter() {}

  static void append(StringBuilder canonical, BookkeepingEntry entry) {
    switch (entry) {
      case BookkeepingEntry.DirectJournal _ -> {}
      case BookkeepingEntry.Reversal _ -> {}
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          RequestFingerprintInventoryEntryWriter.append(canonical, inventoryEntry);
      case AccrualCutoffBookkeepingEntryVariants accrualCutoffEntry ->
          RequestFingerprintAccrualCutoffEntryWriter.append(canonical, accrualCutoffEntry);
      case LatvianPayrollBookkeepingEntryVariants payrollEntry ->
          RequestFingerprintLatvianPayrollEntryWriter.append(canonical, payrollEntry);
      case FixedAssetBookkeepingEntryVariants fixedAssetEntry ->
          RequestFingerprintFixedAssetEntryWriter.append(canonical, fixedAssetEntry);
      case FinancingBookkeepingEntryVariants financingEntry ->
          RequestFingerprintFinancingEntryWriter.append(canonical, financingEntry);
      case RealizedForeignExchangeBookkeepingEntryVariants realizedForeignExchangeEntry ->
          RequestFingerprintRealizedForeignExchangeEntryWriter.append(
              canonical, realizedForeignExchangeEntry);
      case StandardBookkeepingEntryVariants standardEntry ->
          RequestFingerprintStandardEntryWriter.append(canonical, standardEntry);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          appendOpeningPosition(canonical, openingPosition);
    }
  }

  private static void appendOpeningPosition(
      StringBuilder canonical, BookkeepingEntry.OpeningPosition openingPosition) {
    for (int index = 0; index < openingPosition.balances().size(); index++) {
      BookkeepingEntry.OpeningPosition.OpeningAccountBalance balance =
          openingPosition.balances().get(index);
      String prefix = "callerAuthoredEntry.openingBalances[" + index + "].";
      RequestFingerprintEntryFieldWriter.appendField(
          canonical, prefix + "accountCode", balance.accountCode().value());
      RequestFingerprintEntryFieldWriter.appendField(
          canonical, prefix + "side", balance.side().wireValue());
      RequestFingerprintEntryFieldWriter.appendField(
          canonical, prefix + "amountCurrency", balance.amount().currencyCode());
      RequestFingerprintEntryFieldWriter.appendField(
          canonical, prefix + "amountMinorUnits", balance.amount().minorUnits());
      RequestFingerprintEntryFieldWriter.appendField(
          canonical,
          prefix + "quantity",
          balance.quantity() == null ? "" : balance.quantity().value());
    }
  }
}
