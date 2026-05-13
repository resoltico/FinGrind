package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping statement-of-financial-position view. */
public record FinancialPositionView(
    Optional<LocalDate> effectiveDateTo, List<FinancialPositionSectionView> sections) {
  public FinancialPositionView {
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
  }
}
