package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping statement-of-changes-in-equity view. */
public record ChangesInEquityView(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    List<ChangesInEquityRowView> rows,
    List<CurrencyBalance> openingTotals,
    List<CurrencyBalance> movementTotals,
    List<CurrencyBalance> closingTotals) {
  public ChangesInEquityView {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    openingTotals = List.copyOf(Objects.requireNonNull(openingTotals, "openingTotals"));
    movementTotals = List.copyOf(Objects.requireNonNull(movementTotals, "movementTotals"));
    closingTotals = List.copyOf(Objects.requireNonNull(closingTotals, "closingTotals"));
  }
}
