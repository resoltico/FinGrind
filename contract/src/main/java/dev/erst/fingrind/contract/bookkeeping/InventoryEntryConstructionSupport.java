package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Constructor normalization owned by the inventory bookkeeping-entry variants. */
final class InventoryEntryConstructionSupport {
  record InventoryCapitalizationSettledState(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount) {}

  record InventoryCapitalizationOnCreditState(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {}

  record InventoryWriteDownState(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode writeDownLossAccountCode,
      MonetaryAmount amount) {}

  record InventoryShrinkageState(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode shrinkageLossAccountCode,
      QuantityText quantity,
      @Nullable ResolvedInventoryDisposal resolvedInventoryDisposal) {}

  record InventoryCountIncreaseState(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode countGainAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition) {}

  private InventoryEntryConstructionSupport() {}

  static InventoryCapitalizationSettledState inventoryCapitalizationSettled(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredInventoryAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            inventoryAccountCode, "inventoryAccountCode");
    AccountCode requiredCashAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            cashAccountCode, "cashAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryForeignExchangeValidationSupport.requireTypedEntryForeignExchange(
        requiredAmount,
        foreignExchangeDetails,
        BookkeepingEntryForeignExchangeValidationSupport.ForeignExchangeAllowance
            .SPOT_TRANSACTION_ONLY,
        "inventory capitalization");
    BookkeepingEntryTaxValidationSupport.requireTaxSelectionState(
        requiredAmount,
        taxSelection,
        appliedTax,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE);
    return new InventoryCapitalizationSettledState(
        requiredEffectiveDate,
        requiredInventoryAccountCode,
        requiredCashAccountCode,
        requiredAmount);
  }

  static InventoryCapitalizationOnCreditState inventoryCapitalizationOnCredit(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredInventoryAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            inventoryAccountCode, "inventoryAccountCode");
    AccountCode requiredPayableAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            payableAccountCode, "payableAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryForeignExchangeValidationSupport.requireTypedEntryForeignExchange(
        requiredAmount,
        foreignExchangeDetails,
        BookkeepingEntryForeignExchangeValidationSupport.ForeignExchangeAllowance
            .SPOT_TRANSACTION_ONLY,
        "inventoryCapitalizationOnCredit");
    BookkeepingEntryTaxValidationSupport.requireTaxSelectionState(
        requiredAmount,
        taxSelection,
        appliedTax,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE);
    return new InventoryCapitalizationOnCreditState(
        requiredEffectiveDate,
        requiredInventoryAccountCode,
        requiredPayableAccountCode,
        requiredAmount,
        foreignExchangeDetails);
  }

  static InventoryWriteDownState inventoryWriteDown(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode writeDownLossAccountCode,
      MonetaryAmount amount) {
    return new InventoryWriteDownState(
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            inventoryAccountCode, "inventoryAccountCode"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            writeDownLossAccountCode, "writeDownLossAccountCode"),
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount"));
  }

  static InventoryShrinkageState inventoryShrinkage(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode shrinkageLossAccountCode,
      QuantityText quantity,
      @Nullable ResolvedInventoryDisposal resolvedInventoryDisposal) {
    return new InventoryShrinkageState(
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            inventoryAccountCode, "inventoryAccountCode"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            shrinkageLossAccountCode, "shrinkageLossAccountCode"),
        BookkeepingEntryScalarValidationSupport.requirePositiveQuantityText(quantity, "quantity"),
        resolvedInventoryDisposal);
  }

  static InventoryCountIncreaseState inventoryCountIncrease(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode countGainAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition) {
    return new InventoryCountIncreaseState(
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            inventoryAccountCode, "inventoryAccountCode"),
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            countGainAccountCode, "countGainAccountCode"),
        BookkeepingEntryScalarValidationSupport.requirePositiveQuantityText(quantity, "quantity"),
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(unitCost, "unitCost"),
        resolvedInventoryAcquisition);
  }
}
