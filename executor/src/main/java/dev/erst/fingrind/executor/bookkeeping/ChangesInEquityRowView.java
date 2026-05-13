package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import java.util.Objects;

/** Local bookkeeping row inside one statement of changes in equity. */
public record ChangesInEquityRowView(
    String lineCode,
    String lineName,
    boolean synthetic,
    CurrencyBalance openingBalance,
    CurrencyBalance movement,
    CurrencyBalance closingBalance) {
  public ChangesInEquityRowView {
    Objects.requireNonNull(lineCode, "lineCode");
    Objects.requireNonNull(lineName, "lineName");
    Objects.requireNonNull(openingBalance, "openingBalance");
    Objects.requireNonNull(movement, "movement");
    Objects.requireNonNull(closingBalance, "closingBalance");
  }
}
