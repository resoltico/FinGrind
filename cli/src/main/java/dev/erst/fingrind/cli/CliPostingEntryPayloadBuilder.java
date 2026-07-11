package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliOpeningBalancePayload;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Mutable assembly helper for one caller-authored posting-entry CLI payload. */
final class CliPostingEntryPayloadBuilder {
  private final CliPostingEntryPayloadComponents.PayloadCore core;

  private CliPostingEntryPayload.@Nullable InventoryReliefPayload inventoryRelief;
  private CliPostingEntryPayload.@Nullable SettlementAdjunctPayload settlementAdjunct;
  private CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchange;
  private CliTaxJsonModels.@Nullable TaxSelectionPayload taxSelection;
  private CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax;
  private CliBookQueryJsonModels.@Nullable ReversalPayload reversal;
  private @Nullable List<CliOpeningBalancePayload> openingBalances;
  private CliPostingEntryPayload.@Nullable ResolvedInventoryCostingPayload resolvedInventoryCosting;

  CliPostingEntryPayloadBuilder(CliPostingEntryPayloadComponents.PayloadCore core) {
    this.core = Objects.requireNonNull(core, "core");
  }

  CliPostingEntryPayloadBuilder withSettlementAdjunct(
      CliPostingEntryPayload.@Nullable SettlementAdjunctPayload value) {
    settlementAdjunct = value;
    return this;
  }

  CliPostingEntryPayloadBuilder withForeignExchange(
      CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload value) {
    foreignExchange = value;
    return this;
  }

  CliPostingEntryPayloadBuilder withTaxSelection(
      CliTaxJsonModels.@Nullable TaxSelectionPayload value) {
    taxSelection = value;
    return this;
  }

  CliPostingEntryPayloadBuilder withAppliedTax(CliTaxJsonModels.@Nullable AppliedTaxPayload value) {
    appliedTax = value;
    return this;
  }

  CliPostingEntryPayloadBuilder withReversal(CliBookQueryJsonModels.ReversalPayload value) {
    reversal = value;
    return this;
  }

  CliPostingEntryPayloadBuilder withOpeningBalances(List<CliOpeningBalancePayload> value) {
    openingBalances = List.copyOf(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withInventoryRelief(
      CliPostingEntryPayload.@Nullable InventoryReliefPayload value) {
    inventoryRelief = value;
    return this;
  }

  CliPostingEntryPayloadBuilder withResolvedInventoryCosting(
      CliPostingEntryPayload.@Nullable ResolvedInventoryCostingPayload value) {
    resolvedInventoryCosting = value;
    return this;
  }

  CliPostingEntryPayload build() {
    return new CliPostingEntryPayload(
        core.entryKind(),
        core.accounts().cashAccountCode(),
        core.accounts().receivableAccountCode(),
        core.accounts().payableAccountCode(),
        core.accounts().revenueAccountCode(),
        core.accounts().inventoryAccountCode(),
        core.accounts().expenseAccountCode(),
        core.accounts().writeDownLossAccountCode(),
        core.accounts().shrinkageLossAccountCode(),
        core.accounts().countGainAccountCode(),
        core.accounts().equityAccountCode(),
        core.amount(),
        core.quantity(),
        core.unitCost(),
        inventoryRelief,
        settlementAdjunct,
        foreignExchange,
        taxSelection,
        appliedTax,
        reversal,
        openingBalances,
        resolvedInventoryCosting);
  }
}
