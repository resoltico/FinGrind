package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import org.jspecify.annotations.Nullable;

/** Writes caller-authored inventory-event facts into the idempotency fingerprint. */
final class RequestFingerprintInventoryEntryWriter {
  private RequestFingerprintInventoryEntryWriter() {}

  static void append(StringBuilder canonical, InventoryBookkeepingEntryVariants entry) {
    switch (entry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          append(canonical, capitalization);
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          append(canonical, capitalization);
      case InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown ->
          append(canonical, writeDown);
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage ->
          append(canonical, shrinkage);
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease ->
          append(canonical, countIncrease);
    }
  }

  static void append(StringBuilder canonical, BookkeepingEntry.PurchaseSettled purchase) {
    appendPurchase(
        canonical,
        purchase.inventoryAccountCode(),
        "cashAccountCode",
        purchase.cashAccountCode(),
        purchase.quantity(),
        purchase.unitCost(),
        purchase.taxSelection());
  }

  static void append(StringBuilder canonical, BookkeepingEntry.PurchaseOnCredit purchase) {
    appendPurchase(
        canonical,
        purchase.inventoryAccountCode(),
        "payableAccountCode",
        purchase.payableAccountCode(),
        purchase.quantity(),
        purchase.unitCost(),
        purchase.taxSelection());
  }

  static void append(
      StringBuilder canonical,
      InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization) {
    appendCapitalization(
        canonical,
        capitalization.inventoryAccountCode(),
        "cashAccountCode",
        capitalization.cashAccountCode(),
        capitalization.amount(),
        capitalization.taxSelection(),
        capitalization.appliedTax());
  }

  static void append(
      StringBuilder canonical,
      InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization) {
    appendCapitalization(
        canonical,
        capitalization.inventoryAccountCode(),
        "payableAccountCode",
        capitalization.payableAccountCode(),
        capitalization.amount(),
        capitalization.taxSelection(),
        capitalization.appliedTax());
  }

  static void append(
      StringBuilder canonical, InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "inventoryAccountCode", writeDown.inventoryAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "writeDownLossAccountCode", writeDown.writeDownLossAccountCode());
    RequestFingerprintEntryFieldWriter.appendAmount(canonical, writeDown.amount());
  }

  static void append(
      StringBuilder canonical, InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "inventoryAccountCode", shrinkage.inventoryAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "shrinkageLossAccountCode", shrinkage.shrinkageLossAccountCode());
    RequestFingerprintEntryFieldWriter.appendQuantity(canonical, "quantity", shrinkage.quantity());
  }

  static void append(
      StringBuilder canonical,
      InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "inventoryAccountCode", countIncrease.inventoryAccountCode());
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "countGainAccountCode", countIncrease.countGainAccountCode());
    appendQuantityAndUnitCost(canonical, countIncrease.quantity(), countIncrease.unitCost());
  }

  private static void appendPurchase(
      StringBuilder canonical,
      AccountCode inventoryAccountCode,
      String counterpartyField,
      AccountCode counterpartyAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable TaxSelection taxSelection) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "inventoryAccountCode", inventoryAccountCode);
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, counterpartyField, counterpartyAccountCode);
    appendQuantityAndUnitCost(canonical, quantity, unitCost);
    RequestFingerprintEntryFieldWriter.appendTaxSelection(canonical, taxSelection);
  }

  private static void appendCapitalization(
      StringBuilder canonical,
      AccountCode inventoryAccountCode,
      String counterpartyField,
      AccountCode counterpartyAccountCode,
      MonetaryAmount amount,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, "inventoryAccountCode", inventoryAccountCode);
    RequestFingerprintEntryFieldWriter.appendAccountCode(
        canonical, counterpartyField, counterpartyAccountCode);
    RequestFingerprintEntryFieldWriter.appendTaxedAmount(
        canonical, amount, taxSelection, appliedTax);
  }

  private static void appendQuantityAndUnitCost(
      StringBuilder canonical, QuantityText quantity, MonetaryAmount unitCost) {
    RequestFingerprintEntryFieldWriter.appendQuantity(canonical, "quantity", quantity);
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry.unitCostCurrency", unitCost.currencyCode());
    RequestFingerprintEntryFieldWriter.appendField(
        canonical, "callerAuthoredEntry.unitCostMinorUnits", unitCost.minorUnits());
  }
}
