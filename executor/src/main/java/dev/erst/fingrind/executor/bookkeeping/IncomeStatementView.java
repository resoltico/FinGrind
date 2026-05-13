package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping income-statement view. */
public record IncomeStatementView(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    List<IncomeStatementSectionView> sections,
    List<CurrencyBalance> netIncomeTotals) {
  public IncomeStatementView {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    netIncomeTotals = List.copyOf(Objects.requireNonNull(netIncomeTotals, "netIncomeTotals"));
  }
}
