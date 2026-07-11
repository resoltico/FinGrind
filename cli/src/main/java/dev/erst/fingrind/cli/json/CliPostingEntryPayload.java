package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Public JSON payload for one caller-authored posting entry. */
public record CliPostingEntryPayload(
    String entryKind,
    @Nullable String cashAccountCode,
    @Nullable String receivableAccountCode,
    @Nullable String payableAccountCode,
    @Nullable String revenueAccountCode,
    @Nullable String inventoryAccountCode,
    @Nullable String expenseAccountCode,
    @Nullable String writeDownLossAccountCode,
    @Nullable String shrinkageLossAccountCode,
    @Nullable String countGainAccountCode,
    @Nullable String equityAccountCode,
    @Nullable MonetaryAmount amount,
    @Nullable String quantity,
    @Nullable MonetaryAmount unitCost,
    CliPostingEntryPayload.@Nullable InventoryReliefPayload inventoryRelief,
    @Nullable SettlementAdjunctPayload settlementAdjunct,
    CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchange,
    CliTaxJsonModels.@Nullable TaxSelectionPayload taxSelection,
    CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax,
    CliBookQueryJsonModels.@Nullable ReversalPayload reversal,
    @Nullable List<CliOpeningBalancePayload> openingBalances,
    @Nullable ResolvedInventoryCostingPayload resolvedInventoryCosting) {
  /** Validates one caller-authored posting entry payload. */
  public CliPostingEntryPayload {
    entryKind = requireText(entryKind, "entryKind");
    cashAccountCode = requireOptionalText(cashAccountCode, "cashAccountCode");
    receivableAccountCode = requireOptionalText(receivableAccountCode, "receivableAccountCode");
    payableAccountCode = requireOptionalText(payableAccountCode, "payableAccountCode");
    revenueAccountCode = requireOptionalText(revenueAccountCode, "revenueAccountCode");
    inventoryAccountCode = requireOptionalText(inventoryAccountCode, "inventoryAccountCode");
    expenseAccountCode = requireOptionalText(expenseAccountCode, "expenseAccountCode");
    writeDownLossAccountCode =
        requireOptionalText(writeDownLossAccountCode, "writeDownLossAccountCode");
    shrinkageLossAccountCode =
        requireOptionalText(shrinkageLossAccountCode, "shrinkageLossAccountCode");
    countGainAccountCode = requireOptionalText(countGainAccountCode, "countGainAccountCode");
    equityAccountCode = requireOptionalText(equityAccountCode, "equityAccountCode");
    quantity = requireOptionalText(quantity, "quantity");
    openingBalances = openingBalances == null ? null : copyList(openingBalances, "openingBalances");
  }

  /** Public JSON payload for one optional settlement-adjunct line. */
  public record SettlementAdjunctPayload(String accountCode, MonetaryAmount amount) {
    public SettlementAdjunctPayload {
      accountCode = requireText(accountCode, "accountCode");
      Objects.requireNonNull(amount, "amount");
    }
  }

  /** Public JSON payload for one optional trading-sale inventory-relief bundle. */
  public record InventoryReliefPayload(
      String inventoryAccountCode, String costOfSalesAccountCode, String quantity) {
    public InventoryReliefPayload {
      inventoryAccountCode = requireText(inventoryAccountCode, "inventoryAccountCode");
      costOfSalesAccountCode = requireText(costOfSalesAccountCode, "costOfSalesAccountCode");
      quantity = requireText(quantity, "quantity");
    }
  }

  /** Executor-derived sale costing facts retained for committed-posting transparency. */
  public record ResolvedInventoryCostingPayload(
      MonetaryAmount costOfSales,
      String quantityRelieved,
      MonetaryAmount roundedMovingAverageUnitCostProjection) {
    public ResolvedInventoryCostingPayload {
      Objects.requireNonNull(costOfSales, "costOfSales");
      quantityRelieved = requireText(quantityRelieved, "quantityRelieved");
      Objects.requireNonNull(
          roundedMovingAverageUnitCostProjection, "roundedMovingAverageUnitCostProjection");
    }
  }
}
