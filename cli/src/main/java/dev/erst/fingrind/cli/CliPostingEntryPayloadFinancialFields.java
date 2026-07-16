package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import org.jspecify.annotations.Nullable;

/** Mutable financial facts assembled into one posting-entry payload. */
final class CliPostingEntryPayloadFinancialFields {
  private CliPostingEntryPayload.@Nullable InventoryReliefPayload inventoryRelief;
  private CliPostingEntryPayload.@Nullable SettlementAdjunctPayload settlementAdjunct;
  private CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchange;
  private CliTaxJsonModels.@Nullable TaxSelectionPayload taxSelection;
  private CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax;
  private CliPostingEntryPayload.@Nullable ResolvedInventoryCostingPayload resolvedInventoryCosting;

  void withInventoryRelief(CliPostingEntryPayload.@Nullable InventoryReliefPayload value) {
    inventoryRelief = value;
  }

  void withSettlementAdjunct(CliPostingEntryPayload.@Nullable SettlementAdjunctPayload value) {
    settlementAdjunct = value;
  }

  void withForeignExchange(CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload value) {
    foreignExchange = value;
  }

  void withTaxSelection(CliTaxJsonModels.@Nullable TaxSelectionPayload value) {
    taxSelection = value;
  }

  void withAppliedTax(CliTaxJsonModels.@Nullable AppliedTaxPayload value) {
    appliedTax = value;
  }

  void withResolvedInventoryCosting(
      CliPostingEntryPayload.@Nullable ResolvedInventoryCostingPayload value) {
    resolvedInventoryCosting = value;
  }

  CliPostingEntryPayload.@Nullable InventoryReliefPayload inventoryRelief() {
    return inventoryRelief;
  }

  CliPostingEntryPayload.@Nullable SettlementAdjunctPayload settlementAdjunct() {
    return settlementAdjunct;
  }

  CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchange() {
    return foreignExchange;
  }

  CliTaxJsonModels.@Nullable TaxSelectionPayload taxSelection() {
    return taxSelection;
  }

  CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax() {
    return appliedTax;
  }

  CliPostingEntryPayload.@Nullable ResolvedInventoryCostingPayload resolvedInventoryCosting() {
    return resolvedInventoryCosting;
  }
}
