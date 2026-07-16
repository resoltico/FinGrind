package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliFixedAssetPostingJsonModels;
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
  private final CliPostingEntryPayloadFinancialFields financialFields =
      new CliPostingEntryPayloadFinancialFields();
  private final CliPostingEntryPayloadLifecycleFields lifecycleFields =
      new CliPostingEntryPayloadLifecycleFields();

  CliPostingEntryPayloadBuilder(CliPostingEntryPayloadComponents.PayloadCore core) {
    this.core = Objects.requireNonNull(core, "core");
  }

  CliPostingEntryPayloadBuilder withSettlementAdjunct(
      CliPostingEntryPayload.@Nullable SettlementAdjunctPayload value) {
    financialFields.withSettlementAdjunct(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withForeignExchange(
      CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload value) {
    financialFields.withForeignExchange(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withTaxSelection(
      CliTaxJsonModels.@Nullable TaxSelectionPayload value) {
    financialFields.withTaxSelection(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withAppliedTax(CliTaxJsonModels.@Nullable AppliedTaxPayload value) {
    financialFields.withAppliedTax(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withReversal(
      dev.erst.fingrind.cli.json.CliBookQueryJsonModels.ReversalPayload value) {
    lifecycleFields.withReversal(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withOpeningBalances(List<CliOpeningBalancePayload> value) {
    lifecycleFields.withOpeningBalances(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withInventoryRelief(
      CliPostingEntryPayload.@Nullable InventoryReliefPayload value) {
    financialFields.withInventoryRelief(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withResolvedInventoryCosting(
      CliPostingEntryPayload.@Nullable ResolvedInventoryCostingPayload value) {
    financialFields.withResolvedInventoryCosting(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withAccrualCutoff(
      CliPostingEntryPayload.@Nullable AccrualCutoffPayload value) {
    lifecycleFields.withAccrualCutoff(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withLatvianMonthlyPayroll(
      CliPostingEntryPayload.@Nullable LatvianMonthlyPayrollPayload value) {
    lifecycleFields.withLatvianMonthlyPayroll(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withLatvianPayrollSettlement(
      CliPostingEntryPayload.@Nullable LatvianPayrollSettlementPayload value) {
    lifecycleFields.withLatvianPayrollSettlement(value);
    return this;
  }

  CliPostingEntryPayloadBuilder withFixedAsset(
      CliFixedAssetPostingJsonModels.@Nullable FixedAssetPayload value) {
    lifecycleFields.withFixedAsset(value);
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
        financialFields.inventoryRelief(),
        financialFields.settlementAdjunct(),
        financialFields.foreignExchange(),
        financialFields.taxSelection(),
        financialFields.appliedTax(),
        lifecycleFields.reversal(),
        lifecycleFields.openingBalances(),
        financialFields.resolvedInventoryCosting(),
        lifecycleFields.accrualCutoff(),
        lifecycleFields.latvianMonthlyPayroll(),
        lifecycleFields.latvianPayrollSettlement(),
        lifecycleFields.fixedAsset());
  }
}
