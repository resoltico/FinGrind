package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Canonical book-wide trial balance as of one optional effective date. */
public record TrialBalanceReport(Optional<LocalDate> effectiveDateTo, List<TrialBalanceRow> rows) {
  /** Validates one trial-balance report. */
  public TrialBalanceReport(Optional<LocalDate> effectiveDateTo, List<TrialBalanceRow> rows) {
    this.effectiveDateTo = Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    this.rows = ContractDescriptorValidation.copyList(rows, "rows");
  }
}
