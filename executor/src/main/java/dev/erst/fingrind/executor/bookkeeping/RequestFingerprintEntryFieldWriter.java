package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import org.jspecify.annotations.Nullable;

/** Shared caller-authored entry fingerprint field appenders. */
final class RequestFingerprintEntryFieldWriter {
  private RequestFingerprintEntryFieldWriter() {}

  static void appendAccountCode(
      StringBuilder canonical, String fieldName, AccountCode accountCode) {
    appendField(canonical, "callerAuthoredEntry." + fieldName, accountCode.value());
  }

  static void appendAmount(StringBuilder canonical, MonetaryAmount amount) {
    appendField(canonical, "callerAuthoredEntry.amountCurrency", amount.currencyCode());
    appendField(canonical, "callerAuthoredEntry.amountMinorUnits", amount.minorUnits());
  }

  static void appendTaxedAmount(
      StringBuilder canonical,
      MonetaryAmount amount,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    appendAmount(canonical, amount);
    appendField(
        canonical,
        "callerAuthoredEntry.taxSelection.present",
        Boolean.toString(taxSelection != null));
    if (taxSelection != null) {
      appendField(
          canonical,
          "callerAuthoredEntry.taxSelection.taxRegistrationId",
          taxSelection.taxRegistrationId().value());
      appendField(
          canonical, "callerAuthoredEntry.taxSelection.taxCode", taxSelection.taxCode().value());
    }
    appendField(
        canonical, "callerAuthoredEntry.appliedTax.present", Boolean.toString(appliedTax != null));
    if (appliedTax == null) {
      return;
    }
    appendField(canonical, "callerAuthoredEntry.appliedTax.taxCode", appliedTax.taxCode().value());
    appendField(
        canonical, "callerAuthoredEntry.appliedTax.taxCodeName", appliedTax.taxCodeName().value());
    appendField(
        canonical,
        "callerAuthoredEntry.appliedTax.ratePartsPerMillion",
        Integer.toString(appliedTax.rate().partsPerMillionOfWhole()));
    appendField(
        canonical,
        "callerAuthoredEntry.appliedTax.inclusionMode",
        appliedTax.inclusionMode().wireValue());
    appendField(
        canonical,
        "callerAuthoredEntry.appliedTax.applicationKind",
        appliedTax.applicationKind().wireValue());
    appendField(
        canonical,
        "callerAuthoredEntry.appliedTax.taxableMinorUnits",
        appliedTax.taxableAmount().minorUnits());
    appendField(
        canonical,
        "callerAuthoredEntry.appliedTax.taxMinorUnits",
        appliedTax.taxAmount().minorUnits());
    appendField(
        canonical,
        "callerAuthoredEntry.appliedTax.grossMinorUnits",
        appliedTax.grossAmount().minorUnits());
    appendField(
        canonical,
        "callerAuthoredEntry.appliedTax.taxAccountCode",
        appliedTax.taxAccountCode() == null ? "" : appliedTax.taxAccountCode().value());
  }

  static void appendOptionalSettlementAdjunct(
      StringBuilder canonical, @Nullable SettlementAdjunct settlementAdjunct) {
    appendField(
        canonical,
        "callerAuthoredEntry.settlementAdjunct.present",
        Boolean.toString(settlementAdjunct != null));
    if (settlementAdjunct == null) {
      return;
    }
    appendField(
        canonical,
        "callerAuthoredEntry.settlementAdjunct.accountCode",
        settlementAdjunct.accountCode().value());
    appendField(
        canonical,
        "callerAuthoredEntry.settlementAdjunct.amountCurrency",
        settlementAdjunct.amount().currencyCode());
    appendField(
        canonical,
        "callerAuthoredEntry.settlementAdjunct.amountMinorUnits",
        settlementAdjunct.amount().minorUnits());
  }

  static void appendOptionalInventoryRelief(
      StringBuilder canonical, @Nullable InventoryRelief inventoryRelief) {
    appendField(
        canonical,
        "callerAuthoredEntry.inventoryRelief.present",
        Boolean.toString(inventoryRelief != null));
    if (inventoryRelief == null) {
      return;
    }
    appendField(
        canonical,
        "callerAuthoredEntry.inventoryRelief.inventoryAccountCode",
        inventoryRelief.inventoryAccountCode().value());
    appendField(
        canonical,
        "callerAuthoredEntry.inventoryRelief.costOfSalesAccountCode",
        inventoryRelief.costOfSalesAccountCode().value());
    appendField(
        canonical,
        "callerAuthoredEntry.inventoryRelief.amountCurrency",
        inventoryRelief.amount().currencyCode());
    appendField(
        canonical,
        "callerAuthoredEntry.inventoryRelief.amountMinorUnits",
        inventoryRelief.amount().minorUnits());
  }

  static void appendOptionalForeignExchangeDetails(
      StringBuilder canonical, @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.present",
        Boolean.toString(foreignExchangeDetails != null));
    if (foreignExchangeDetails == null) {
      return;
    }
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.transactionCurrency",
        foreignExchangeDetails.transactionAmount().currencyCode());
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.transactionMinorUnits",
        foreignExchangeDetails.transactionAmount().minorUnits());
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.functionalCurrency",
        foreignExchangeDetails.functionalAmount().currencyCode());
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.functionalMinorUnits",
        foreignExchangeDetails.functionalAmount().minorUnits());
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.quotedTransactionMinorUnits",
        foreignExchangeDetails.quotedExchangeRate().transactionCurrencyAmount().minorUnits());
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.quotedFunctionalMinorUnits",
        foreignExchangeDetails.quotedExchangeRate().functionalCurrencyAmount().minorUnits());
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.quotedOn",
        foreignExchangeDetails.quotedExchangeRate().quotedOn().toString());
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.quoteSource",
        foreignExchangeDetails.quotedExchangeRate().quoteSource());
    appendField(
        canonical,
        "callerAuthoredEntry.foreignExchange.treatmentKind",
        foreignExchangeDetails.treatmentKind().wireValue());
  }

  private static void appendField(StringBuilder canonical, String fieldName, String value) {
    RequestFingerprintOwner.append(canonical, fieldName, value);
  }
}
