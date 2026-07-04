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
      @Nullable InventoryRelief inventoryRelief) {}

  record SaleOnCreditState(
      LocalDate effectiveDate,
      AccountCode receivableAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief) {}

  record PurchaseSettledState(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount) {}

  record PurchaseOnCreditState(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount) {}

  record ExpenseSettledState(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount) {}

  record ExpenseOnCreditState(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount) {}

  private BookkeepingEntryConstructionSupport() {}

  static SaleSettledState saleSettled(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredCashAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(cashAccountCode, "cashAccountCode");
    AccountCode requiredRevenueAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            revenueAccountCode, "revenueAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    InventoryRelief requiredInventoryRelief =
        BookkeepingEntryValidationSupport.requireOptionalInventoryRelief(
            inventoryRelief, requiredAmount, "inventoryRelief");
    BookkeepingEntryValidationSupport.requireTypedEntryForeignExchange(
        requiredAmount,
        foreignExchangeDetails,
        BookkeepingEntryValidationSupport.ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY,
        "sale");
    BookkeepingEntryValidationSupport.requireTaxSelectionState(
        requiredAmount, taxSelection, appliedTax, TaxApplicationKind.OUTPUT_SALE);
    return new SaleSettledState(
        requiredEffectiveDate,
        requiredCashAccountCode,
        requiredRevenueAccountCode,
        requiredAmount,
        requiredInventoryRelief);
  }

  static SaleOnCreditState saleOnCredit(
      LocalDate effectiveDate,
      AccountCode receivableAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredReceivableAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            receivableAccountCode, "receivableAccountCode");
    AccountCode requiredRevenueAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            revenueAccountCode, "revenueAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    InventoryRelief requiredInventoryRelief =
        BookkeepingEntryValidationSupport.requireOptionalInventoryRelief(
            inventoryRelief, requiredAmount, "inventoryRelief");
    BookkeepingEntryValidationSupport.requireTaxSelectionState(
        requiredAmount, taxSelection, appliedTax, TaxApplicationKind.OUTPUT_SALE);
    return new SaleOnCreditState(
        requiredEffectiveDate,
        requiredReceivableAccountCode,
        requiredRevenueAccountCode,
        requiredAmount,
        requiredInventoryRelief);
  }

  static PurchaseSettledState purchaseSettled(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount amount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredInventoryAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            inventoryAccountCode, "inventoryAccountCode");
    AccountCode requiredCashAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(cashAccountCode, "cashAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryValidationSupport.requireTypedEntryForeignExchange(
        requiredAmount,
        foreignExchangeDetails,
        BookkeepingEntryValidationSupport.ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY,
        "purchase");
    return new PurchaseSettledState(
        requiredEffectiveDate,
        requiredInventoryAccountCode,
        requiredCashAccountCode,
        requiredAmount);
  }

  static PurchaseOnCreditState purchaseOnCredit(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode payableAccountCode,
      MonetaryAmount amount) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredInventoryAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            inventoryAccountCode, "inventoryAccountCode");
    AccountCode requiredPayableAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            payableAccountCode, "payableAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    return new PurchaseOnCreditState(
        requiredEffectiveDate,
        requiredInventoryAccountCode,
        requiredPayableAccountCode,
        requiredAmount);
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
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredExpenseAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            expenseAccountCode, "expenseAccountCode");
    AccountCode requiredCashAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(cashAccountCode, "cashAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryValidationSupport.requireTypedEntryForeignExchange(
        requiredAmount,
        foreignExchangeDetails,
        BookkeepingEntryValidationSupport.ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY,
        "expense");
    BookkeepingEntryValidationSupport.requireTaxSelectionState(
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
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryValidationSupport.requireEffectiveDate(effectiveDate);
    AccountCode requiredExpenseAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            expenseAccountCode, "expenseAccountCode");
    AccountCode requiredPayableAccountCode =
        BookkeepingEntryValidationSupport.requireAccountCode(
            payableAccountCode, "payableAccountCode");
    MonetaryAmount requiredAmount =
        BookkeepingEntryValidationSupport.requirePositiveAmount(amount, "amount");
    BookkeepingEntryValidationSupport.requireTaxSelectionState(
        requiredAmount,
        taxSelection,
        appliedTax,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE);
    return new ExpenseOnCreditState(
        requiredEffectiveDate,
        requiredExpenseAccountCode,
        requiredPayableAccountCode,
        requiredAmount);
  }
}
