package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Canonical statement of financial position for one selected book. */
public record FinancialPositionReport(
    Optional<LocalDate> effectiveDateTo, List<FinancialPositionSection> sections) {
  /** Validates one financial-position report. */
  public FinancialPositionReport {
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    sections = ContractDescriptorValidation.copyList(sections, "sections");
  }
}
