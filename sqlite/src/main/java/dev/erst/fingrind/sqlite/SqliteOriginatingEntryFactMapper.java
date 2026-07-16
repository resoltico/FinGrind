package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.bookkeeping.StandardBookkeepingEntryVariants;
import org.jspecify.annotations.Nullable;

/** Maps retained originating-entry facts onto the persisted posting-fact columns. */
final class SqliteOriginatingEntryFactMapper {
  private SqliteOriginatingEntryFactMapper() {}

  static void bindOriginatingEntry(
      SqliteNativeStatement statement,
      @Nullable BookkeepingEntry retainedOriginatingEntry,
      @Nullable BookkeepingEntry resolvedOriginatingEntry) {
    bindOriginatingEntryFactValues(
        statement, originatingEntryFactValues(retainedOriginatingEntry, resolvedOriginatingEntry));
  }

  private static OriginatingEntryFactValues originatingEntryFactValues(
      @Nullable BookkeepingEntry retainedOriginatingEntry,
      @Nullable BookkeepingEntry resolvedOriginatingEntry) {
    BookkeepingEntry originatingEntry =
        requiresExecutorResolvedFacts(retainedOriginatingEntry)
            ? java.util.Objects.requireNonNull(
                resolvedOriginatingEntry,
                "Executor-owned posting facts require an executor-resolved entry.")
            : retainedOriginatingEntry;
    if (originatingEntry == null) {
      return OriginatingEntryFactValues.empty();
    }
    return switch (originatingEntry) {
      case InventoryBookkeepingEntryVariants inventoryEntry ->
          SqliteInventoryOriginatingEntryFactValues.originatingEntryFactValues(inventoryEntry);
      case AccrualCutoffBookkeepingEntryVariants accrualCutoffEntry ->
          SqliteAccrualCutoffOriginatingEntryFactValues.originatingEntryFactValues(
              accrualCutoffEntry);
      case LatvianPayrollBookkeepingEntryVariants _ -> OriginatingEntryFactValues.empty();
      case FixedAssetBookkeepingEntryVariants fixedAssetEntry ->
          SqliteFixedAssetOriginatingEntryFactValues.originatingEntryFactValues(fixedAssetEntry);
      case FinancingBookkeepingEntryVariants financingEntry ->
          SqliteFinancingOriginatingEntryFactValues.originatingEntryFactValues(financingEntry);
      case RealizedForeignExchangeBookkeepingEntryVariants foreignExchangeEntry ->
          SqliteRealizedForeignExchangeOriginatingEntryFactValues.originatingEntryFactValues(
              foreignExchangeEntry);
      case StandardBookkeepingEntryVariants standardEntry ->
          standardOriginatingEntryFactValues(standardEntry);
      case BookkeepingEntry.ScalarFactFree _ -> OriginatingEntryFactValues.empty();
    };
  }

  private static OriginatingEntryFactValues standardOriginatingEntryFactValues(
      StandardBookkeepingEntryVariants originatingEntry) {
    return switch (originatingEntry) {
      case BookkeepingEntry.SaleSettled sale ->
          simpleOriginatingEntryFactValues(
              sale.cashAccountCode().value(),
              sale.revenueAccountCode().value(),
              sale.amount(),
              sale.inventoryRelief() == null ? null : sale.inventoryRelief().quantity().value());
      case BookkeepingEntry.SaleOnCredit sale ->
          simpleOriginatingEntryFactValues(
              sale.receivableAccountCode().value(),
              sale.revenueAccountCode().value(),
              sale.amount(),
              sale.inventoryRelief() == null ? null : sale.inventoryRelief().quantity().value());
      case BookkeepingEntry.PurchaseSettled purchase ->
          purchaseOriginatingEntryFactValues(
              purchase.inventoryAccountCode().value(),
              purchase.cashAccountCode().value(),
              purchase.quantity().value(),
              purchase.unitCost());
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          purchaseOriginatingEntryFactValues(
              purchase.inventoryAccountCode().value(),
              purchase.payableAccountCode().value(),
              purchase.quantity().value(),
              purchase.unitCost());
      case BookkeepingEntry.ExpenseSettled expense ->
          simpleOriginatingEntryFactValues(
              expense.expenseAccountCode().value(),
              expense.cashAccountCode().value(),
              expense.amount(),
              null);
      case BookkeepingEntry.ExpenseOnCredit expense ->
          simpleOriginatingEntryFactValues(
              expense.expenseAccountCode().value(),
              expense.payableAccountCode().value(),
              expense.amount(),
              null);
      case BookkeepingEntry.Receipt receipt ->
          settlementOriginatingEntryFactValues(
              receipt.cashAccountCode().value(),
              receipt.receivableAccountCode().value(),
              receipt.amount(),
              receipt.settlementAdjunct());
      case BookkeepingEntry.Payment payment ->
          settlementOriginatingEntryFactValues(
              payment.payableAccountCode().value(),
              payment.cashAccountCode().value(),
              payment.amount(),
              payment.settlementAdjunct());
      case BookkeepingEntry.OwnerContribution contribution ->
          simpleOriginatingEntryFactValues(
              contribution.cashAccountCode().value(),
              contribution.equityAccountCode().value(),
              contribution.amount(),
              null);
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          simpleOriginatingEntryFactValues(
              withdrawal.equityAccountCode().value(),
              withdrawal.cashAccountCode().value(),
              withdrawal.amount(),
              null);
    };
  }

  private static void bindOriginatingEntryFactValues(
      SqliteNativeStatement statement, OriginatingEntryFactValues factValues) {
    statement.bindText(4, factValues.primaryDebitAccountCode());
    statement.bindText(5, factValues.primaryCreditAccountCode());
    statement.bindText(6, factValues.adjunctAccountCode());
    statement.bindText(7, factValues.amountCurrencyCode());
    bindOptionalLong(statement, 8, factValues.amountMinorUnits());
    bindOptionalLong(statement, 9, factValues.adjunctAmountMinorUnits());
    statement.bindText(10, factValues.quantity());
    statement.bindText(11, factValues.unitCostCurrencyCode());
    bindOptionalLong(statement, 12, factValues.unitCostMinorUnits());
  }

  private static boolean requiresExecutorResolvedFacts(
      @Nullable BookkeepingEntry retainedOriginatingEntry) {
    return retainedOriginatingEntry instanceof AccrualCutoffBookkeepingEntryVariants
        || retainedOriginatingEntry instanceof FixedAssetBookkeepingEntryVariants
        || retainedOriginatingEntry instanceof FinancingBookkeepingEntryVariants
        || retainedOriginatingEntry instanceof RealizedForeignExchangeBookkeepingEntryVariants;
  }

  static OriginatingEntryFactValues simpleOriginatingEntryFactValues(
      String primaryDebitAccountCode,
      String primaryCreditAccountCode,
      MonetaryAmount amount,
      @Nullable String quantity) {
    return new OriginatingEntryFactValues(
        primaryDebitAccountCode,
        primaryCreditAccountCode,
        null,
        amount.currencyCode(),
        Long.parseLong(amount.minorUnits()),
        null,
        quantity,
        null,
        null);
  }

  static OriginatingEntryFactValues purchaseOriginatingEntryFactValues(
      String primaryDebitAccountCode,
      String primaryCreditAccountCode,
      String quantity,
      MonetaryAmount unitCost) {
    return new OriginatingEntryFactValues(
        primaryDebitAccountCode,
        primaryCreditAccountCode,
        null,
        null,
        null,
        null,
        quantity,
        unitCost.currencyCode(),
        Long.parseLong(unitCost.minorUnits()));
  }

  private static OriginatingEntryFactValues settlementOriginatingEntryFactValues(
      String primaryDebitAccountCode,
      String primaryCreditAccountCode,
      MonetaryAmount amount,
      @Nullable SettlementAdjunct settlementAdjunct) {
    return new OriginatingEntryFactValues(
        primaryDebitAccountCode,
        primaryCreditAccountCode,
        settlementAdjunct == null ? null : settlementAdjunct.accountCode().value(),
        amount.currencyCode(),
        Long.parseLong(amount.minorUnits()),
        settlementAdjunct == null ? null : Long.parseLong(settlementAdjunct.amount().minorUnits()),
        null,
        null,
        null);
  }

  static OriginatingEntryFactValues quantityOnlyOriginatingEntryFactValues(
      String primaryDebitAccountCode, String primaryCreditAccountCode, String quantity) {
    return new OriginatingEntryFactValues(
        primaryDebitAccountCode,
        primaryCreditAccountCode,
        null,
        null,
        null,
        null,
        quantity,
        null,
        null);
  }

  private static void bindOptionalLong(
      SqliteNativeStatement statement, int parameterIndex, @Nullable Long value) {
    if (value == null) {
      statement.bindNull(parameterIndex);
      return;
    }
    statement.bindLong(parameterIndex, value);
  }

  record OriginatingEntryFactValues(
      @Nullable String primaryDebitAccountCode,
      @Nullable String primaryCreditAccountCode,
      @Nullable String adjunctAccountCode,
      @Nullable String amountCurrencyCode,
      @Nullable Long amountMinorUnits,
      @Nullable Long adjunctAmountMinorUnits,
      @Nullable String quantity,
      @Nullable String unitCostCurrencyCode,
      @Nullable Long unitCostMinorUnits) {
    static OriginatingEntryFactValues empty() {
      return new OriginatingEntryFactValues(null, null, null, null, null, null, null, null, null);
    }
  }
}
