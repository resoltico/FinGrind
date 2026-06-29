package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping section inside one cash-flow statement. */
public record CashFlowSectionView(
    CashFlowSectionKind sectionKind, List<CashFlowRowView> rows, List<CurrencyBalance> totals) {
  public CashFlowSectionView {
    Objects.requireNonNull(sectionKind, "sectionKind");
    rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    totals = List.copyOf(Objects.requireNonNull(totals, "totals"));
  }
}
