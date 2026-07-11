package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.executor.spi.TaxRegistrationLookupStore;
import java.math.BigInteger;
import java.util.Objects;

/** Resolves typed sale and expense requests through the declared tax-registration context. */
final class TaxPostingResolution {
  private TaxPostingResolution() {}

  static BookkeepingEntry resolve(BookkeepingEntry entry, TaxRegistrationLookupStore store) {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(store, "store");
    return switch (entry) {
      case BookkeepingEntry.SaleSettled sale -> resolveSaleSettled(sale, store);
      case BookkeepingEntry.SaleOnCredit sale -> resolveSaleOnCredit(sale, store);
      case BookkeepingEntry.PurchaseSettled purchase -> resolvePurchaseSettled(purchase, store);
      case BookkeepingEntry.PurchaseOnCredit purchase -> resolvePurchaseOnCredit(purchase, store);
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          resolveCapitalizationSettled(capitalization, store);
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          resolveCapitalizationOnCredit(capitalization, store);
      case BookkeepingEntry.ExpenseSettled expense -> resolveExpenseSettled(expense, store);
      case BookkeepingEntry.ExpenseOnCredit expense -> resolveExpenseOnCredit(expense, store);
      default -> entry;
    };
  }

  static boolean requiresInventoryQuantityResolution(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.PurchaseSettled purchase -> purchase.taxSelection() != null;
      case BookkeepingEntry.PurchaseOnCredit purchase -> purchase.taxSelection() != null;
      default -> false;
    };
  }

  private static BookkeepingEntry.PurchaseSettled resolvePurchaseSettled(
      BookkeepingEntry.PurchaseSettled purchase, TaxRegistrationLookupStore store) {
    if (purchase.taxSelection() == null || purchase.appliedTax() != null) {
      return purchase;
    }
    var acquisition = purchase.resolvedInventoryAcquisition();
    if (acquisition == null) {
      throw new IllegalStateException(
          "purchaseSettled tax resolution requires executor-owned quantity resolution.");
    }
    AppliedTax appliedTax =
        resolvedInventoryInputTax(purchase.taxSelection(), store, acquisition.preTaxCost());
    return new BookkeepingEntry.PurchaseSettled(
        purchase.effectiveDate(),
        purchase.inventoryAccountCode(),
        purchase.cashAccountCode(),
        purchase.quantity(),
        purchase.unitCost(),
        purchase.resolvedInventoryAcquisition(),
        purchase.foreignExchangeDetails(),
        purchase.taxSelection(),
        appliedTax);
  }

  private static BookkeepingEntry.PurchaseOnCredit resolvePurchaseOnCredit(
      BookkeepingEntry.PurchaseOnCredit purchase, TaxRegistrationLookupStore store) {
    if (purchase.taxSelection() == null || purchase.appliedTax() != null) {
      return purchase;
    }
    var acquisition = purchase.resolvedInventoryAcquisition();
    if (acquisition == null) {
      throw new IllegalStateException(
          "purchaseOnCredit tax resolution requires executor-owned quantity resolution.");
    }
    AppliedTax appliedTax =
        resolvedInventoryInputTax(purchase.taxSelection(), store, acquisition.preTaxCost());
    return new BookkeepingEntry.PurchaseOnCredit(
        purchase.effectiveDate(),
        purchase.inventoryAccountCode(),
        purchase.payableAccountCode(),
        purchase.quantity(),
        purchase.unitCost(),
        purchase.resolvedInventoryAcquisition(),
        purchase.foreignExchangeDetails(),
        purchase.taxSelection(),
        appliedTax);
  }

  private static InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled
      resolveCapitalizationSettled(
          InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization,
          TaxRegistrationLookupStore store) {
    if (capitalization.taxSelection() == null || capitalization.appliedTax() != null) {
      return capitalization;
    }
    return new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
        capitalization.effectiveDate(),
        capitalization.inventoryAccountCode(),
        capitalization.cashAccountCode(),
        capitalization.amount(),
        capitalization.foreignExchangeDetails(),
        capitalization.taxSelection(),
        resolvedInventoryInputTax(capitalization.taxSelection(), store, capitalization.amount()));
  }

  private static InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit
      resolveCapitalizationOnCredit(
          InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization,
          TaxRegistrationLookupStore store) {
    if (capitalization.taxSelection() == null || capitalization.appliedTax() != null) {
      return capitalization;
    }
    return new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
        capitalization.effectiveDate(),
        capitalization.inventoryAccountCode(),
        capitalization.payableAccountCode(),
        capitalization.amount(),
        capitalization.foreignExchangeDetails(),
        capitalization.taxSelection(),
        resolvedInventoryInputTax(capitalization.taxSelection(), store, capitalization.amount()));
  }

  private static BookkeepingEntry.SaleSettled resolveSaleSettled(
      BookkeepingEntry.SaleSettled sale, TaxRegistrationLookupStore store) {
    if (sale.taxSelection() == null || sale.appliedTax() != null) {
      return sale;
    }
    DeclaredTaxRegistration registration = requireRegistration(sale.taxSelection(), store);
    TaxCodeDefinition code = requireCode(registration, sale.taxSelection());
    if (code.applicationKind() != TaxApplicationKind.OUTPUT_SALE) {
      throw new IllegalArgumentException(
          "Sale taxSelection must resolve to applicationKind OUTPUT_SALE.");
    }
    return new BookkeepingEntry.SaleSettled(
        sale.effectiveDate(),
        sale.cashAccountCode(),
        sale.revenueAccountCode(),
        sale.amount(),
        sale.inventoryRelief(),
        sale.resolvedInventoryCosting(),
        sale.foreignExchangeDetails(),
        sale.taxSelection(),
        appliedTax(sale.taxSelection(), registration, code, sale.amount()));
  }

  private static BookkeepingEntry.SaleOnCredit resolveSaleOnCredit(
      BookkeepingEntry.SaleOnCredit sale, TaxRegistrationLookupStore store) {
    if (sale.taxSelection() == null || sale.appliedTax() != null) {
      return sale;
    }
    DeclaredTaxRegistration registration = requireRegistration(sale.taxSelection(), store);
    TaxCodeDefinition code = requireCode(registration, sale.taxSelection());
    if (code.applicationKind() != TaxApplicationKind.OUTPUT_SALE) {
      throw new IllegalArgumentException(
          "Sale taxSelection must resolve to applicationKind OUTPUT_SALE.");
    }
    return new BookkeepingEntry.SaleOnCredit(
        sale.effectiveDate(),
        sale.receivableAccountCode(),
        sale.revenueAccountCode(),
        sale.amount(),
        sale.inventoryRelief(),
        sale.resolvedInventoryCosting(),
        sale.foreignExchangeDetails(),
        sale.taxSelection(),
        appliedTax(sale.taxSelection(), registration, code, sale.amount()));
  }

  private static BookkeepingEntry.ExpenseSettled resolveExpenseSettled(
      BookkeepingEntry.ExpenseSettled expense, TaxRegistrationLookupStore store) {
    if (expense.taxSelection() == null || expense.appliedTax() != null) {
      return expense;
    }
    DeclaredTaxRegistration registration = requireRegistration(expense.taxSelection(), store);
    TaxCodeDefinition code = requireCode(registration, expense.taxSelection());
    if (code.applicationKind() == TaxApplicationKind.OUTPUT_SALE) {
      throw new IllegalArgumentException(
          "Expense taxSelection cannot resolve to applicationKind OUTPUT_SALE.");
    }
    return new BookkeepingEntry.ExpenseSettled(
        expense.effectiveDate(),
        expense.expenseAccountCode(),
        expense.cashAccountCode(),
        expense.amount(),
        expense.foreignExchangeDetails(),
        expense.taxSelection(),
        appliedTax(expense.taxSelection(), registration, code, expense.amount()));
  }

  private static BookkeepingEntry.ExpenseOnCredit resolveExpenseOnCredit(
      BookkeepingEntry.ExpenseOnCredit expense, TaxRegistrationLookupStore store) {
    if (expense.taxSelection() == null || expense.appliedTax() != null) {
      return expense;
    }
    DeclaredTaxRegistration registration = requireRegistration(expense.taxSelection(), store);
    TaxCodeDefinition code = requireCode(registration, expense.taxSelection());
    if (code.applicationKind() == TaxApplicationKind.OUTPUT_SALE) {
      throw new IllegalArgumentException(
          "Expense taxSelection cannot resolve to applicationKind OUTPUT_SALE.");
    }
    return new BookkeepingEntry.ExpenseOnCredit(
        expense.effectiveDate(),
        expense.expenseAccountCode(),
        expense.payableAccountCode(),
        expense.amount(),
        expense.foreignExchangeDetails(),
        expense.taxSelection(),
        appliedTax(expense.taxSelection(), registration, code, expense.amount()));
  }

  private static DeclaredTaxRegistration requireRegistration(
      TaxSelection selection, TaxRegistrationLookupStore store) {
    return store
        .findTaxRegistration(selection.taxRegistrationId())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown taxRegistrationId '%s'."
                        .formatted(selection.taxRegistrationId().value())));
  }

  private static AppliedTax resolvedInventoryInputTax(
      TaxSelection selection, TaxRegistrationLookupStore store, MonetaryAmount preTaxCost) {
    DeclaredTaxRegistration registration = requireRegistration(selection, store);
    TaxCodeDefinition code = requireCode(registration, selection);
    if (code.applicationKind() == TaxApplicationKind.OUTPUT_SALE) {
      throw new IllegalArgumentException(
          "Inventory input taxSelection cannot resolve to applicationKind OUTPUT_SALE.");
    }
    if (code.inclusionMode() != dev.erst.fingrind.contract.tax.TaxInclusionMode.EXCLUSIVE) {
      throw new IllegalArgumentException(
          "Inventory unitCost and capitalization amount are pre-VAT inputs, so taxSelection must use EXCLUSIVE inclusionMode.");
    }
    return appliedTax(selection, registration, code, preTaxCost);
  }

  private static TaxCodeDefinition requireCode(
      DeclaredTaxRegistration registration, TaxSelection selection) {
    return registration.taxCodes().stream()
        .filter(code -> code.taxCode().equals(selection.taxCode()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown taxCode '%s' for taxRegistrationId '%s'."
                        .formatted(
                            selection.taxCode().value(), selection.taxRegistrationId().value())));
  }

  private static AppliedTax appliedTax(
      TaxSelection selection,
      DeclaredTaxRegistration registration,
      TaxCodeDefinition code,
      MonetaryAmount operatorAmount) {
    MonetaryAmount taxableAmount;
    MonetaryAmount taxAmount;
    MonetaryAmount grossAmount;
    if (code.inclusionMode() == dev.erst.fingrind.contract.tax.TaxInclusionMode.EXCLUSIVE) {
      taxableAmount = operatorAmount;
      taxAmount =
          MonetaryAmount.of(
              dev.erst.fingrind.core.Money.ofMinorUnits(
                  operatorAmount.toMoney().currencyUnit(),
                  roundHalfUp(
                      operatorAmount.toMoney().minorUnits(),
                      code.rate().partsPerMillionOfWhole(),
                      TaxRate.WHOLE)));
      grossAmount = MonetaryAmount.of(taxableAmount.toMoney().plus(taxAmount.toMoney()));
    } else {
      grossAmount = operatorAmount;
      long taxMinor =
          roundHalfUp(
              grossAmount.toMoney().minorUnits(),
              code.rate().partsPerMillionOfWhole(),
              TaxRate.WHOLE + code.rate().partsPerMillionOfWhole());
      taxAmount =
          MonetaryAmount.of(
              dev.erst.fingrind.core.Money.ofMinorUnits(
                  grossAmount.toMoney().currencyUnit(), taxMinor));
      taxableAmount = MonetaryAmount.of(grossAmount.toMoney().minus(taxAmount.toMoney()));
    }
    return new AppliedTax(
        selection.taxRegistrationId(),
        selection.taxCode(),
        code.taxCodeName(),
        code.rate(),
        code.inclusionMode(),
        code.applicationKind(),
        taxableAmount,
        taxAmount,
        grossAmount,
        switch (code.applicationKind()) {
          case OUTPUT_SALE -> registration.payableAccountCode();
          case INPUT_EXPENSE_RECOVERABLE -> registration.recoverableAccountCode();
          case INPUT_EXPENSE_NONRECOVERABLE -> null;
        });
  }

  private static long roundHalfUp(long amountMinorUnits, int multiplier, int denominator) {
    BigInteger numerator =
        BigInteger.valueOf(amountMinorUnits).multiply(BigInteger.valueOf(multiplier));
    BigInteger divisor = BigInteger.valueOf(denominator);
    BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(divisor);
    BigInteger rounded = quotientAndRemainder[0];
    if (quotientAndRemainder[1].shiftLeft(1).compareTo(divisor) >= 0) {
      rounded = rounded.add(BigInteger.ONE);
    }
    return rounded.longValueExact();
  }
}
