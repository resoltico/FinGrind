package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/** Maps each typed record command to the context-owned bookkeeping entry kind it requires. */
final class ProtocolTypedRecordEntryKinds {
  private ProtocolTypedRecordEntryKinds() {}

  static Map<OperationId, BookkeepingEntryKind> entryKinds() {
    var entryKinds = new EnumMap<OperationId, BookkeepingEntryKind>(OperationId.class);
    Stream.of(
            standardSaleEntryKinds(),
            inventoryEntryKinds(),
            accrualCutoffEntryKinds(),
            latvianPayrollEntryKinds(),
            fixedAssetEntryKinds(),
            financingEntryKinds(),
            realizedForeignExchangeEntryKinds(),
            standardLedgerEntryKinds())
        .forEach(entryKinds::putAll);
    return Map.copyOf(entryKinds);
  }

  private static Map<OperationId, BookkeepingEntryKind> standardSaleEntryKinds() {
    return Map.of(
        OperationId.RECORD_SALE_SETTLED,
        BookkeepingEntryKind.SALE_SETTLED,
        OperationId.RECORD_SALE_ON_CREDIT,
        BookkeepingEntryKind.SALE_ON_CREDIT);
  }

  private static Map<OperationId, BookkeepingEntryKind> inventoryEntryKinds() {
    return Map.ofEntries(
        Map.entry(OperationId.RECORD_PURCHASE_SETTLED, BookkeepingEntryKind.PURCHASE_SETTLED),
        Map.entry(OperationId.RECORD_PURCHASE_ON_CREDIT, BookkeepingEntryKind.PURCHASE_ON_CREDIT),
        Map.entry(
            OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED),
        Map.entry(
            OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT),
        Map.entry(
            OperationId.RECORD_INVENTORY_WRITE_DOWN, BookkeepingEntryKind.INVENTORY_WRITE_DOWN),
        Map.entry(OperationId.RECORD_INVENTORY_SHRINKAGE, BookkeepingEntryKind.INVENTORY_SHRINKAGE),
        Map.entry(
            OperationId.RECORD_INVENTORY_COUNT_INCREASE,
            BookkeepingEntryKind.INVENTORY_COUNT_INCREASE));
  }

  private static Map<OperationId, BookkeepingEntryKind> accrualCutoffEntryKinds() {
    return Map.of(
        OperationId.RECORD_PREPAYMENT,
        BookkeepingEntryKind.PREPAYMENT,
        OperationId.RECORD_DEFERRED_REVENUE,
        BookkeepingEntryKind.DEFERRED_REVENUE,
        OperationId.RECORD_ACCRUED_EXPENSE,
        BookkeepingEntryKind.ACCRUED_EXPENSE,
        OperationId.RECORD_ACCRUAL_CUTOFF_RECOGNITION,
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
        OperationId.RECORD_ACCRUED_EXPENSE_SETTLEMENT,
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT);
  }

  private static Map<OperationId, BookkeepingEntryKind> latvianPayrollEntryKinds() {
    return Map.of(
        OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL,
        BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
        OperationId.RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        OperationId.RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE,
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE);
  }

  private static Map<OperationId, BookkeepingEntryKind> fixedAssetEntryKinds() {
    return Map.of(
        OperationId.RECORD_FIXED_ASSET_CAPITALIZATION,
        BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
        OperationId.RECORD_FIXED_ASSET_DEPRECIATION,
        BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
        OperationId.RECORD_FIXED_ASSET_DISPOSAL,
        BookkeepingEntryKind.FIXED_ASSET_DISPOSAL);
  }

  private static Map<OperationId, BookkeepingEntryKind> financingEntryKinds() {
    return Map.of(
        OperationId.RECORD_FINANCING_BORROWING,
        BookkeepingEntryKind.FINANCING_BORROWING,
        OperationId.RECORD_FINANCING_PRINCIPAL_REPAYMENT,
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
        OperationId.RECORD_FINANCING_INTEREST_ACCRUAL,
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
        OperationId.RECORD_FINANCING_INTEREST_PAYMENT,
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT);
  }

  private static Map<OperationId, BookkeepingEntryKind> realizedForeignExchangeEntryKinds() {
    return Map.of(
        OperationId.RECORD_FOREIGN_CURRENCY_OBLIGATION,
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
        OperationId.RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT);
  }

  private static Map<OperationId, BookkeepingEntryKind> standardLedgerEntryKinds() {
    return Map.ofEntries(
        Map.entry(OperationId.RECORD_EXPENSE_SETTLED, BookkeepingEntryKind.EXPENSE_SETTLED),
        Map.entry(OperationId.RECORD_EXPENSE_ON_CREDIT, BookkeepingEntryKind.EXPENSE_ON_CREDIT),
        Map.entry(OperationId.RECORD_RECEIPT, BookkeepingEntryKind.RECEIPT),
        Map.entry(OperationId.RECORD_PAYMENT, BookkeepingEntryKind.PAYMENT),
        Map.entry(OperationId.RECORD_OWNER_CONTRIBUTION, BookkeepingEntryKind.OWNER_CONTRIBUTION),
        Map.entry(OperationId.RECORD_OWNER_WITHDRAWAL, BookkeepingEntryKind.OWNER_WITHDRAWAL),
        Map.entry(OperationId.RECORD_OPENING_POSITION, BookkeepingEntryKind.OPENING_POSITION),
        Map.entry(OperationId.RECORD_REVERSAL, BookkeepingEntryKind.REVERSAL));
  }
}
