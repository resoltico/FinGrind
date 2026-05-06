package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping trial-balance view. */
public record TrialBalanceView(
    Optional<LocalDate> effectiveDateTo, List<TrialBalanceRowView> rows) {
  public TrialBalanceView {
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    Objects.requireNonNull(rows, "rows");
    rows = List.copyOf(rows);
  }
}
