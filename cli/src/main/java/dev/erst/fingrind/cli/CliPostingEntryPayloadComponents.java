package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliOpeningBalancePayload;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryCosting;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxSelection;
import org.jspecify.annotations.Nullable;

/** Shared component mappers for caller-authored posting-entry CLI payloads. */
final class CliPostingEntryPayloadComponents {
  private CliPostingEntryPayloadComponents() {}

  record PayloadAccounts(
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String writeDownLossAccountCode,
      @Nullable String shrinkageLossAccountCode,
      @Nullable String countGainAccountCode,
      @Nullable String equityAccountCode) {
    static PayloadAccounts none() {
      return new PayloadAccounts(null, null, null, null, null, null, null, null, null, null);
    }
  }

  record TaxPayloadInput(@Nullable TaxSelection selection, @Nullable AppliedTax appliedTax) {}

  record SalePayloadInput(
      String entryKind,
      PayloadAccounts accounts,
      MonetaryAmount amount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      TaxPayloadInput taxPayloadInput) {}

  record PayloadCore(
      String entryKind,
      PayloadAccounts accounts,
      @Nullable MonetaryAmount amount,
      @Nullable String quantity,
      @Nullable MonetaryAmount unitCost) {}

  static CliPostingEntryPayloadBuilder payload(
      String entryKind, PayloadAccounts accounts, @Nullable MonetaryAmount amount) {
    return payload(entryKind, accounts, amount, null, null);
  }

  static CliPostingEntryPayloadBuilder payload(
      String entryKind,
      PayloadAccounts accounts,
      @Nullable MonetaryAmount amount,
      @Nullable String quantity,
      @Nullable MonetaryAmount unitCost) {
    return new CliPostingEntryPayloadBuilder(
        new PayloadCore(entryKind, accounts, amount, quantity, unitCost));
  }

  static CliOpeningBalancePayload openingBalancePayload(
      BookkeepingEntry.OpeningPosition.OpeningAccountBalance balance) {
    return new CliOpeningBalancePayload(
        balance.accountCode().value(),
        balance.side().wireValue(),
        balance.amount(),
        balance.quantity() == null ? null : balance.quantity().value());
  }

  static CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchangePayload(
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    if (foreignExchangeDetails == null) {
      return null;
    }
    return new CliForeignExchangeJsonModels.ForeignExchangePayload(
        foreignExchangeDetails.transactionAmount(),
        foreignExchangeDetails.functionalAmount(),
        new CliForeignExchangeJsonModels.QuotedExchangeRatePayload(
            foreignExchangeDetails.quotedExchangeRate().transactionCurrencyAmount(),
            foreignExchangeDetails.quotedExchangeRate().functionalCurrencyAmount(),
            foreignExchangeDetails.quotedExchangeRate().quotedOn().toString(),
            foreignExchangeDetails.quotedExchangeRate().quoteSource()),
        foreignExchangeDetails.treatmentKind().wireValue());
  }

  static CliPostingEntryPayload.@Nullable SettlementAdjunctPayload settlementAdjunctPayload(
      @Nullable SettlementAdjunct settlementAdjunct) {
    if (settlementAdjunct == null) {
      return null;
    }
    return new CliPostingEntryPayload.SettlementAdjunctPayload(
        settlementAdjunct.accountCode().value(), settlementAdjunct.amount());
  }

  static CliPostingEntryPayload.@Nullable InventoryReliefPayload inventoryReliefPayload(
      @Nullable InventoryRelief inventoryRelief) {
    if (inventoryRelief == null) {
      return null;
    }
    return new CliPostingEntryPayload.InventoryReliefPayload(
        inventoryRelief.inventoryAccountCode().value(),
        inventoryRelief.costOfSalesAccountCode().value(),
        inventoryRelief.quantity().value());
  }

  static CliPostingEntryPayload.@Nullable ResolvedInventoryCostingPayload
      resolvedInventoryCostingPayload(@Nullable ResolvedInventoryCosting resolvedInventoryCosting) {
    if (resolvedInventoryCosting == null) {
      return null;
    }
    return new CliPostingEntryPayload.ResolvedInventoryCostingPayload(
        MonetaryAmount.of(resolvedInventoryCosting.costOfSales()),
        resolvedInventoryCosting.quantityRelieved().canonicalDecimal(),
        MonetaryAmount.of(resolvedInventoryCosting.roundedMovingAverageUnitCostProjection()));
  }

  static void addTaxAndForeignExchange(
      CliPostingEntryPayloadBuilder builder,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      TaxPayloadInput taxPayloadInput) {
    builder
        .withForeignExchange(foreignExchangePayload(foreignExchangeDetails))
        .withTaxSelection(
            taxPayloadInput.selection() == null
                ? null
                : CliTaxPayloadMapper.taxSelectionPayload(taxPayloadInput.selection()))
        .withAppliedTax(
            taxPayloadInput.appliedTax() == null
                ? null
                : CliTaxPayloadMapper.appliedTaxPayload(taxPayloadInput.appliedTax()));
  }
}
