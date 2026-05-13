package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** One per-currency total row inside a period summary. */
public record PeriodCurrencySummary(CurrencyBalance totals) {
  /** Validates one period-currency summary row. */
  public PeriodCurrencySummary {
    Objects.requireNonNull(totals, "totals");
  }
}
