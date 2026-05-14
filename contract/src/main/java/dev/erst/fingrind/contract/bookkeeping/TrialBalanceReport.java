package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Canonical book-wide trial balance as of one optional effective date. */
public record TrialBalanceReport(
    BookIdentity bookIdentity,
    Optional<LocalDate> effectiveDateTo,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    List<TrialBalanceRow> rows) {
  /** Validates one trial-balance report. */
  public TrialBalanceReport(
      BookIdentity bookIdentity,
      Optional<LocalDate> effectiveDateTo,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage,
      List<TrialBalanceRow> rows) {
    this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    this.effectiveDateTo = Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    this.comparativeEffectiveDateRange =
        Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    this.postingCoverage = Objects.requireNonNull(postingCoverage, "postingCoverage");
    this.rows = ContractDescriptorValidation.copyList(rows, "rows");
  }
}
