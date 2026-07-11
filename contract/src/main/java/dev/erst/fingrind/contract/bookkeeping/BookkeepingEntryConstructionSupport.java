package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Package-private constructor normalization for the public bookkeeping-entry surface. */
final class BookkeepingEntryConstructionSupport {
  record SaleSettledState(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting) {}

  record SaleOnCreditState(
      LocalDate effectiveDate,
      AccountCode receivableAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {}

  record PurchaseSettledState(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode cashAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition) {}

  record PurchaseOnCreditState(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode payableAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {}

  record ExpenseSettledState(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount) {}

  record ExpenseOnCreditState(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {}

  private BookkeepingEntryConstructionSupport() {}

  static SaleSettledState saleSettled(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredCashAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            cashAccountCode, "cashAccountCode");
    AccountCode requiredRevenueAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            revenueAccountCode, "revenueAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryForeignExchangeValidationSupport.requireTypedEntryForeignExchange(
        requiredAmount,
        foreignExchangeDetails,
        BookkeepingEntryForeignExchangeValidationSupport.ForeignExchangeAllowance
            .SPOT_TRANSACTION_ONLY,
        "sale");
    BookkeepingEntryTaxValidationSupport.requireTaxSelectionState(
        requiredAmount, taxSelection, appliedTax, TaxApplicationKind.OUTPUT_SALE);
    return new SaleSettledState(
        requiredEffectiveDate,
        requiredCashAccountCode,
        requiredRevenueAccountCode,
        requiredAmount,
        inventoryRelief,
        resolvedInventoryCosting);
  }

  static SaleOnCreditState saleOnCredit(
      LocalDate effectiveDate,
      AccountCode receivableAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredReceivableAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            receivableAccountCode, "receivableAccountCode");
    AccountCode requiredRevenueAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            revenueAccountCode, "revenueAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryForeignExchangeValidationSupport.requireTypedEntryForeignExchange(
        requiredAmount,
        foreignExchangeDetails,
        BookkeepingEntryForeignExchangeValidationSupport.ForeignExchangeAllowance
            .SPOT_TRANSACTION_ONLY,
        "saleOnCredit");
    BookkeepingEntryTaxValidationSupport.requireTaxSelectionState(
        requiredAmount, taxSelection, appliedTax, TaxApplicationKind.OUTPUT_SALE);
    return new SaleOnCreditState(
        requiredEffectiveDate,
        requiredReceivableAccountCode,
        requiredRevenueAccountCode,
        requiredAmount,
        inventoryRelief,
        resolvedInventoryCosting,
        foreignExchangeDetails);
  }

  static PurchaseSettledState purchaseSettled(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode cashAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition,
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
    QuantityText requiredQuantity =
        BookkeepingEntryScalarValidationSupport.requirePositiveQuantityText(quantity, "quantity");
    MonetaryAmount requiredUnitCost =
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(unitCost, "unitCost");
    BookkeepingEntryForeignExchangeValidationSupport.requireTypedEntryForeignExchangeTreatment(
        foreignExchangeDetails,
        BookkeepingEntryForeignExchangeValidationSupport.ForeignExchangeAllowance
            .SPOT_TRANSACTION_ONLY,
        "purchase");
    requirePurchaseTaxSelectionState(resolvedInventoryAcquisition, taxSelection, appliedTax);
    return new PurchaseSettledState(
        requiredEffectiveDate,
        requiredInventoryAccountCode,
        requiredCashAccountCode,
        requiredQuantity,
        requiredUnitCost,
        resolvedInventoryAcquisition);
  }

  static PurchaseOnCreditState purchaseOnCredit(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode payableAccountCode,
      QuantityText quantity,
      MonetaryAmount unitCost,
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition,
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
    QuantityText requiredQuantity =
        BookkeepingEntryScalarValidationSupport.requirePositiveQuantityText(quantity, "quantity");
    MonetaryAmount requiredUnitCost =
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(unitCost, "unitCost");
    BookkeepingEntryForeignExchangeValidationSupport.requireTypedEntryForeignExchangeTreatment(
        foreignExchangeDetails,
        BookkeepingEntryForeignExchangeValidationSupport.ForeignExchangeAllowance
            .SPOT_TRANSACTION_ONLY,
        "purchaseOnCredit");
    requirePurchaseTaxSelectionState(resolvedInventoryAcquisition, taxSelection, appliedTax);
    return new PurchaseOnCreditState(
        requiredEffectiveDate,
        requiredInventoryAccountCode,
        requiredPayableAccountCode,
        requiredQuantity,
        requiredUnitCost,
        resolvedInventoryAcquisition,
        foreignExchangeDetails);
  }

  private static void requirePurchaseTaxSelectionState(
      @Nullable ResolvedInventoryAcquisition resolvedInventoryAcquisition,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    if (appliedTax == null) {
      if (taxSelection == null) {
        return;
      }
      return;
    }
    MonetaryAmount taxableAmount =
        BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryAcquisition(
                resolvedInventoryAcquisition, "purchase")
            .preTaxCost();
    BookkeepingEntryTaxValidationSupport.requireTaxSelectionState(
        taxableAmount,
        taxSelection,
        appliedTax,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE);
  }

  static ExpenseSettledState expenseSettled(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredExpenseAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            expenseAccountCode, "expenseAccountCode");
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
        "expense");
    BookkeepingEntryTaxValidationSupport.requireTaxSelectionState(
        requiredAmount,
        taxSelection,
        appliedTax,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE);
    return new ExpenseSettledState(
        requiredEffectiveDate, requiredExpenseAccountCode, requiredCashAccountCode, requiredAmount);
  }

  static ExpenseOnCreditState expenseOnCredit(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredExpenseAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            expenseAccountCode, "expenseAccountCode");
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
        "expenseOnCredit");
    BookkeepingEntryTaxValidationSupport.requireTaxSelectionState(
        requiredAmount,
        taxSelection,
        appliedTax,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE);
    return new ExpenseOnCreditState(
        requiredEffectiveDate,
        requiredExpenseAccountCode,
        requiredPayableAccountCode,
        requiredAmount,
        foreignExchangeDetails);
  }
}
