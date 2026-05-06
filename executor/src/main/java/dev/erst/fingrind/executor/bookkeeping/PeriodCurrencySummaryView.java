package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** Local bookkeeping currency-total row inside one period-summary view. */
public record PeriodCurrencySummaryView(CurrencyBalance totals) {
  public PeriodCurrencySummaryView {
    Objects.requireNonNull(totals, "totals");
  }
}
