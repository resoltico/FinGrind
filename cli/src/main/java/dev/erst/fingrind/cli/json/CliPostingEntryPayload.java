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
    @Nullable String equityAccountCode,
    @Nullable MonetaryAmount amount,
    CliPostingEntryPayload.@Nullable InventoryReliefPayload inventoryRelief,
    @Nullable SettlementAdjunctPayload settlementAdjunct,
    CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchange,
    CliTaxJsonModels.@Nullable TaxSelectionPayload taxSelection,
    CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax,
    CliBookQueryJsonModels.@Nullable ReversalPayload reversal,
    @Nullable List<CliOpeningBalancePayload> openingBalances) {
  /** Validates one caller-authored posting entry payload. */
  public CliPostingEntryPayload {
    entryKind = requireText(entryKind, "entryKind");
    cashAccountCode = requireOptionalText(cashAccountCode, "cashAccountCode");
    receivableAccountCode = requireOptionalText(receivableAccountCode, "receivableAccountCode");
    payableAccountCode = requireOptionalText(payableAccountCode, "payableAccountCode");
    revenueAccountCode = requireOptionalText(revenueAccountCode, "revenueAccountCode");
    inventoryAccountCode = requireOptionalText(inventoryAccountCode, "inventoryAccountCode");
    expenseAccountCode = requireOptionalText(expenseAccountCode, "expenseAccountCode");
    equityAccountCode = requireOptionalText(equityAccountCode, "equityAccountCode");
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
      String inventoryAccountCode, String costOfSalesAccountCode, MonetaryAmount amount) {
    public InventoryReliefPayload {
      inventoryAccountCode = requireText(inventoryAccountCode, "inventoryAccountCode");
      costOfSalesAccountCode = requireText(costOfSalesAccountCode, "costOfSalesAccountCode");
      Objects.requireNonNull(amount, "amount");
    }
  }
}
