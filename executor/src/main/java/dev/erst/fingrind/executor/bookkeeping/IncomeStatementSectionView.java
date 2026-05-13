package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping section inside one income statement. */
public record IncomeStatementSectionView(
    AccountType accountType, List<IncomeStatementRowView> rows, List<CurrencyBalance> totals) {
  public IncomeStatementSectionView {
    Objects.requireNonNull(accountType, "accountType");
    rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    totals = List.copyOf(Objects.requireNonNull(totals, "totals"));
  }
}
