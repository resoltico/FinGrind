package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping trial-balance view. */
public record TrialBalanceView(
    BookIdentity bookIdentity,
    Optional<LocalDate> effectiveDateAsOf,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    List<TrialBalanceRowView> rows,
    List<TrialBalanceRowView> comparativeRows) {
  public TrialBalanceView {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    comparativeRows = List.copyOf(Objects.requireNonNull(comparativeRows, "comparativeRows"));
  }
}
