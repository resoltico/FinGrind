package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliFixedAssetPostingJsonModels;
import dev.erst.fingrind.cli.json.CliOpeningBalancePayload;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Mutable lifecycle facts assembled into one posting-entry payload. */
final class CliPostingEntryPayloadLifecycleFields {
  private CliBookQueryJsonModels.@Nullable ReversalPayload reversal;
  private @Nullable List<CliOpeningBalancePayload> openingBalances;
  private CliPostingEntryPayload.@Nullable AccrualCutoffPayload accrualCutoff;
  private CliPostingEntryPayload.@Nullable LatvianMonthlyPayrollPayload latvianMonthlyPayroll;
  private CliPostingEntryPayload.@Nullable LatvianPayrollSettlementPayload latvianPayrollSettlement;
  private CliFixedAssetPostingJsonModels.@Nullable FixedAssetPayload fixedAsset;

  void withReversal(CliBookQueryJsonModels.ReversalPayload value) {
    reversal = value;
  }

  void withOpeningBalances(List<CliOpeningBalancePayload> value) {
    openingBalances = List.copyOf(value);
  }

  void withAccrualCutoff(CliPostingEntryPayload.@Nullable AccrualCutoffPayload value) {
    accrualCutoff = value;
  }

  void withLatvianMonthlyPayroll(
      CliPostingEntryPayload.@Nullable LatvianMonthlyPayrollPayload value) {
    latvianMonthlyPayroll = value;
  }

  void withLatvianPayrollSettlement(
      CliPostingEntryPayload.@Nullable LatvianPayrollSettlementPayload value) {
    latvianPayrollSettlement = value;
  }

  void withFixedAsset(CliFixedAssetPostingJsonModels.@Nullable FixedAssetPayload value) {
    fixedAsset = value;
  }

  CliBookQueryJsonModels.@Nullable ReversalPayload reversal() {
    return reversal;
  }

  @Nullable List<CliOpeningBalancePayload> openingBalances() {
    return openingBalances;
  }

  CliPostingEntryPayload.@Nullable AccrualCutoffPayload accrualCutoff() {
    return accrualCutoff;
  }

  CliPostingEntryPayload.@Nullable LatvianMonthlyPayrollPayload latvianMonthlyPayroll() {
    return latvianMonthlyPayroll;
  }

  CliPostingEntryPayload.@Nullable LatvianPayrollSettlementPayload latvianPayrollSettlement() {
    return latvianPayrollSettlement;
  }

  CliFixedAssetPostingJsonModels.@Nullable FixedAssetPayload fixedAsset() {
    return fixedAsset;
  }
}
