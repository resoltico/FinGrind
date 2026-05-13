package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping section inside one statement of financial position. */
public record FinancialPositionSectionView(
    AccountType accountType, List<FinancialPositionRowView> rows, List<CurrencyBalance> totals) {
  public FinancialPositionSectionView {
    Objects.requireNonNull(accountType, "accountType");
    rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    totals = List.copyOf(Objects.requireNonNull(totals, "totals"));
  }
}
