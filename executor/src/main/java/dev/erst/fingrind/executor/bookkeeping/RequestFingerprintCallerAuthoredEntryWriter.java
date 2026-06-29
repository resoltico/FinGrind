package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import org.jspecify.annotations.Nullable;

/** Canonical caller-authored entry fingerprint fields for posting request fingerprints. */
final class RequestFingerprintCallerAuthoredEntryWriter {
  private RequestFingerprintCallerAuthoredEntryWriter() {}

  static void append(StringBuilder canonical, BookkeepingEntry entry) {
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.entryKind", entry.entryKind().wireValue());
    switch (entry) {
      case BookkeepingEntry.DirectJournal _ -> {}
      case BookkeepingEntry.Sale sale -> appendSale(canonical, sale);
      case BookkeepingEntry.Expense expense -> appendExpense(canonical, expense);
      case BookkeepingEntry.OwnerContribution contribution ->
          appendOwnerContribution(canonical, contribution);
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          appendOwnerWithdrawal(canonical, withdrawal);
      case BookkeepingEntry.OpeningPosition _ -> {}
      case BookkeepingEntry.Reversal _ -> {}
    }
    appendForeignExchangeDetails(canonical, entry.foreignExchangeDetails());
  }

  private static void appendSale(StringBuilder canonical, BookkeepingEntry.Sale sale) {
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.cashAccountCode", sale.cashAccountCode().value());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.revenueAccountCode", sale.revenueAccountCode().value());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.amountCurrency", sale.amount().currencyCode());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.amountMinorUnits", sale.amount().minorUnits());
    appendTaxSelection(canonical, sale.taxSelection());
    appendAppliedTax(canonical, sale.appliedTax());
  }

  private static void appendExpense(StringBuilder canonical, BookkeepingEntry.Expense expense) {
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.expenseAccountCode", expense.expenseAccountCode().value());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.cashAccountCode", expense.cashAccountCode().value());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.amountCurrency", expense.amount().currencyCode());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.amountMinorUnits", expense.amount().minorUnits());
    appendTaxSelection(canonical, expense.taxSelection());
    appendAppliedTax(canonical, expense.appliedTax());
  }

  private static void appendOwnerContribution(
      StringBuilder canonical, BookkeepingEntry.OwnerContribution contribution) {
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.cashAccountCode", contribution.cashAccountCode().value());
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.equityAccountCode",
        contribution.equityAccountCode().value());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.amountCurrency", contribution.amount().currencyCode());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.amountMinorUnits", contribution.amount().minorUnits());
  }

  private static void appendOwnerWithdrawal(
      StringBuilder canonical, BookkeepingEntry.OwnerWithdrawal withdrawal) {
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.equityAccountCode", withdrawal.equityAccountCode().value());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.cashAccountCode", withdrawal.cashAccountCode().value());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.amountCurrency", withdrawal.amount().currencyCode());
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.amountMinorUnits", withdrawal.amount().minorUnits());
  }

  private static void appendTaxSelection(
      StringBuilder canonical, @Nullable TaxSelection taxSelection) {
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.taxSelection.present",
        Boolean.toString(taxSelection != null));
    if (taxSelection != null) {
      RequestFingerprintOwner.append(
          canonical,
          "callerAuthoredEntry.taxSelection.taxRegistrationId",
          taxSelection.taxRegistrationId().value());
      RequestFingerprintOwner.append(
          canonical, "callerAuthoredEntry.taxSelection.taxCode", taxSelection.taxCode().value());
    }
  }

  private static void appendAppliedTax(StringBuilder canonical, @Nullable AppliedTax appliedTax) {
    RequestFingerprintOwner.append(
        canonical, "callerAuthoredEntry.appliedTax.present", Boolean.toString(appliedTax != null));
    if (appliedTax != null) {
      RequestFingerprintOwner.append(
          canonical, "callerAuthoredEntry.appliedTax.taxCode", appliedTax.taxCode().value());
      RequestFingerprintOwner.append(
          canonical,
          "callerAuthoredEntry.appliedTax.taxCodeName",
          appliedTax.taxCodeName().value());
      RequestFingerprintOwner.append(
          canonical,
          "callerAuthoredEntry.appliedTax.ratePartsPerMillion",
          Integer.toString(appliedTax.rate().partsPerMillionOfWhole()));
      RequestFingerprintOwner.append(
          canonical,
          "callerAuthoredEntry.appliedTax.inclusionMode",
          appliedTax.inclusionMode().wireValue());
      RequestFingerprintOwner.append(
          canonical,
          "callerAuthoredEntry.appliedTax.applicationKind",
          appliedTax.applicationKind().wireValue());
      RequestFingerprintOwner.append(
          canonical,
          "callerAuthoredEntry.appliedTax.taxableMinorUnits",
          appliedTax.taxableAmount().minorUnits());
      RequestFingerprintOwner.append(
          canonical,
          "callerAuthoredEntry.appliedTax.taxMinorUnits",
          appliedTax.taxAmount().minorUnits());
      RequestFingerprintOwner.append(
          canonical,
          "callerAuthoredEntry.appliedTax.grossMinorUnits",
          appliedTax.grossAmount().minorUnits());
      RequestFingerprintOwner.append(
          canonical,
          "callerAuthoredEntry.appliedTax.taxAccountCode",
          appliedTax.taxAccountCode() == null ? "" : appliedTax.taxAccountCode().value());
    }
  }

  private static void appendForeignExchangeDetails(
      StringBuilder canonical, @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.present",
        Boolean.toString(foreignExchangeDetails != null));
    if (foreignExchangeDetails == null) {
      return;
    }
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.transactionCurrency",
        foreignExchangeDetails.transactionAmount().currencyCode());
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.transactionMinorUnits",
        foreignExchangeDetails.transactionAmount().minorUnits());
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.functionalCurrency",
        foreignExchangeDetails.functionalAmount().currencyCode());
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.functionalMinorUnits",
        foreignExchangeDetails.functionalAmount().minorUnits());
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.quotedTransactionMinorUnits",
        foreignExchangeDetails.quotedExchangeRate().transactionCurrencyAmount().minorUnits());
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.quotedFunctionalMinorUnits",
        foreignExchangeDetails.quotedExchangeRate().functionalCurrencyAmount().minorUnits());
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.quotedOn",
        foreignExchangeDetails.quotedExchangeRate().quotedOn().toString());
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.quoteSource",
        foreignExchangeDetails.quotedExchangeRate().quoteSource());
    RequestFingerprintOwner.append(
        canonical,
        "callerAuthoredEntry.foreignExchange.treatmentKind",
        foreignExchangeDetails.treatmentKind().wireValue());
  }
}
