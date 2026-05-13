package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** Local bookkeeping line inside one statement-of-financial-position section. */
public record FinancialPositionRowView(
    String lineCode,
    String lineName,
    AccountType lineType,
    boolean synthetic,
    CurrencyBalance balance) {
  public FinancialPositionRowView {
    Objects.requireNonNull(lineCode, "lineCode");
    Objects.requireNonNull(lineName, "lineName");
    Objects.requireNonNull(lineType, "lineType");
    Objects.requireNonNull(balance, "balance");
  }
}
