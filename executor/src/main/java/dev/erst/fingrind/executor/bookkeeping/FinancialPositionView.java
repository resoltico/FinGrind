package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping statement-of-financial-position view. */
public record FinancialPositionView(
    BookIdentity bookIdentity,
    Optional<LocalDate> effectiveDateAsOf,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    boolean accountingEquationBalanced,
    List<FinancialPositionSectionView> sections,
    List<FinancialPositionSectionView> comparativeSections) {
  public FinancialPositionView {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    comparativeSections =
        List.copyOf(Objects.requireNonNull(comparativeSections, "comparativeSections"));
  }
}
