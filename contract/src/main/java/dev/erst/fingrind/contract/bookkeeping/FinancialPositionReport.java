package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Canonical statement of financial position for one selected book. */
public record FinancialPositionReport(
    BookIdentity bookIdentity,
    Optional<LocalDate> effectiveDateAsOf,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    List<FinancialPositionSection> sections,
    List<FinancialPositionSection> comparativeSections) {
  /** Validates one financial-position report. */
  public FinancialPositionReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    sections = ContractDescriptorValidation.copyList(sections, "sections");
    comparativeSections =
        ContractDescriptorValidation.copyList(comparativeSections, "comparativeSections");
  }
}
