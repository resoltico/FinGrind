package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliOpeningBalancePayload;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Immutable builder for the caller-authored posting-entry CLI payload. */
record CliPostingEntryPayloadBuilder(
    String entryKind,
    @Nullable String cashAccountCode,
    @Nullable String receivableAccountCode,
    @Nullable String payableAccountCode,
    @Nullable String revenueAccountCode,
    @Nullable String inventoryAccountCode,
    @Nullable String expenseAccountCode,
    @Nullable String equityAccountCode,
    @Nullable MonetaryAmount amount,
    CliPostingEntryPayload.@Nullable InventoryReliefPayload inventoryRelief,
    CliPostingEntryPayload.@Nullable SettlementAdjunctPayload settlementAdjunct,
    CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchange,
    CliTaxJsonModels.@Nullable TaxSelectionPayload taxSelection,
    CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax,
    CliBookQueryJsonModels.@Nullable ReversalPayload reversal,
    @Nullable List<CliOpeningBalancePayload> openingBalances) {
  CliPostingEntryPayloadBuilder(
      String entryKind,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      @Nullable MonetaryAmount amount) {
    this(
        entryKind,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        amount,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  CliPostingEntryPayloadBuilder withSettlementAdjunct(
      CliPostingEntryPayload.@Nullable SettlementAdjunctPayload value) {
    return new CliPostingEntryPayloadBuilder(
        entryKind,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        amount,
        inventoryRelief,
        value,
        foreignExchange,
        taxSelection,
        appliedTax,
        reversal,
        openingBalances);
  }

  CliPostingEntryPayloadBuilder withForeignExchange(
      CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload value) {
    return new CliPostingEntryPayloadBuilder(
        entryKind,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        amount,
        inventoryRelief,
        settlementAdjunct,
        value,
        taxSelection,
        appliedTax,
        reversal,
        openingBalances);
  }

  CliPostingEntryPayloadBuilder withTaxSelection(
      CliTaxJsonModels.@Nullable TaxSelectionPayload value) {
    return new CliPostingEntryPayloadBuilder(
        entryKind,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        amount,
        inventoryRelief,
        settlementAdjunct,
        foreignExchange,
        value,
        appliedTax,
        reversal,
        openingBalances);
  }

  CliPostingEntryPayloadBuilder withAppliedTax(CliTaxJsonModels.@Nullable AppliedTaxPayload value) {
    return new CliPostingEntryPayloadBuilder(
        entryKind,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        amount,
        inventoryRelief,
        settlementAdjunct,
        foreignExchange,
        taxSelection,
        value,
        reversal,
        openingBalances);
  }

  CliPostingEntryPayloadBuilder withReversal(CliBookQueryJsonModels.ReversalPayload value) {
    return new CliPostingEntryPayloadBuilder(
        entryKind,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        amount,
        inventoryRelief,
        settlementAdjunct,
        foreignExchange,
        taxSelection,
        appliedTax,
        value,
        openingBalances);
  }

  CliPostingEntryPayloadBuilder withOpeningBalances(List<CliOpeningBalancePayload> value) {
    return new CliPostingEntryPayloadBuilder(
        entryKind,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        amount,
        inventoryRelief,
        settlementAdjunct,
        foreignExchange,
        taxSelection,
        appliedTax,
        reversal,
        value);
  }

  CliPostingEntryPayloadBuilder withInventoryRelief(
      CliPostingEntryPayload.@Nullable InventoryReliefPayload value) {
    return new CliPostingEntryPayloadBuilder(
        entryKind,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        amount,
        value,
        settlementAdjunct,
        foreignExchange,
        taxSelection,
        appliedTax,
        reversal,
        openingBalances);
  }

  CliPostingEntryPayload build() {
    return new CliPostingEntryPayload(
        entryKind,
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        equityAccountCode,
        amount,
        inventoryRelief,
        settlementAdjunct,
        foreignExchange,
        taxSelection,
        appliedTax,
        reversal,
        openingBalances);
  }
}
