package dev.erst.fingrind.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Canonical book-wide trial balance as of one optional effective date. */
public record TrialBalanceReport(Optional<LocalDate> effectiveDateTo, List<TrialBalanceRow> rows) {
  /** Validates one trial-balance report. */
  public TrialBalanceReport(
      Optional<LocalDate> effectiveDateTo, @Nullable List<TrialBalanceRow> rows) {
    this.effectiveDateTo = Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    this.rows = rows == null ? List.of() : List.copyOf(rows);
  }
}
