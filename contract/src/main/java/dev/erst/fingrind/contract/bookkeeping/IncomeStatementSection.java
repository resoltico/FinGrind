package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;
import java.util.Objects;

/** One income-statement section grouped by nominal account type. */
public record IncomeStatementSection(
    AccountType accountType, List<IncomeStatementRow> rows, List<CurrencyBalance> totals) {
  /** Validates one income-statement section. */
  public IncomeStatementSection {
    Objects.requireNonNull(accountType, "accountType");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
    totals = ContractDescriptorValidation.copyList(totals, "totals");
  }
}
