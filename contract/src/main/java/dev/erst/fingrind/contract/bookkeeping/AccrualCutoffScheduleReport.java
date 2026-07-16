package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact schedule of accrued, deferred, and prepaid lifecycle balances for one book. */
public record AccrualCutoffScheduleReport(
    BookIdentity bookIdentity,
    Optional<LocalDate> effectiveDateAsOf,
    List<AccrualCutoffScheduleRow> rows) {
  /** Validates one published accrual cut-off schedule. */
  public AccrualCutoffScheduleReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
  }
}
