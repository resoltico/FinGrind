package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Public JSON payload for one caller-authored posting entry. */
public record CliPostingEntryPayload(
    String entryKind,
    @Nullable String cashAccountCode,
    @Nullable String revenueAccountCode,
    @Nullable String expenseAccountCode,
    @Nullable String equityAccountCode,
    @Nullable MonetaryAmount amount,
    CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchange,
    CliTaxJsonModels.@Nullable TaxSelectionPayload taxSelection,
    CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax,
    CliBookQueryJsonModels.@Nullable ReversalPayload reversal,
    @Nullable List<CliOpeningBalancePayload> openingBalances) {
  /** Validates one caller-authored posting entry payload. */
  public CliPostingEntryPayload {
    entryKind = requireText(entryKind, "entryKind");
    cashAccountCode = requireOptionalText(cashAccountCode, "cashAccountCode");
    revenueAccountCode = requireOptionalText(revenueAccountCode, "revenueAccountCode");
    expenseAccountCode = requireOptionalText(expenseAccountCode, "expenseAccountCode");
    equityAccountCode = requireOptionalText(equityAccountCode, "equityAccountCode");
    openingBalances = openingBalances == null ? null : copyList(openingBalances, "openingBalances");
  }
}
