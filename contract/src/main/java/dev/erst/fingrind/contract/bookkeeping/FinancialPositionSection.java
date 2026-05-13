package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;
import java.util.Objects;

/** One statement-of-financial-position section grouped by account type. */
public record FinancialPositionSection(
    AccountType accountType, List<FinancialPositionRow> rows, List<CurrencyBalance> totals) {
  /** Validates one financial-position section. */
  public FinancialPositionSection {
    Objects.requireNonNull(accountType, "accountType");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
    totals = ContractDescriptorValidation.copyList(totals, "totals");
  }
}
