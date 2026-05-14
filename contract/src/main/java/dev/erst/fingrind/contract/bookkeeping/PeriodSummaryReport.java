package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Canonical bounded period summary for one selected book. */
public record PeriodSummaryReport(
    BookIdentity bookIdentity,
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    PostingCoverage postingCoverage,
    int postingCount,
    int postingLineCount,
    int accountsTouched,
    List<PeriodCurrencySummary> currencyTotals,
    List<PeriodAccountActivityRow> accountActivity) {
  /** Validates one period summary report. */
  public PeriodSummaryReport(
      BookIdentity bookIdentity,
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo,
      PostingCoverage postingCoverage,
      int postingCount,
      int postingLineCount,
      int accountsTouched,
      List<PeriodCurrencySummary> currencyTotals,
      List<PeriodAccountActivityRow> accountActivity) {
    this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    this.effectiveDateFrom = Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    this.effectiveDateTo = Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    this.postingCoverage = Objects.requireNonNull(postingCoverage, "postingCoverage");
    this.postingCount = postingCount;
    this.postingLineCount = postingLineCount;
    this.accountsTouched = accountsTouched;
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    if (postingCount < 0) {
      throw new IllegalArgumentException("postingCount must not be negative.");
    }
    if (postingLineCount < 0) {
      throw new IllegalArgumentException("postingLineCount must not be negative.");
    }
    if (accountsTouched < 0) {
      throw new IllegalArgumentException("accountsTouched must not be negative.");
    }
    this.currencyTotals = ContractDescriptorValidation.copyList(currencyTotals, "currencyTotals");
    this.accountActivity =
        ContractDescriptorValidation.copyList(accountActivity, "accountActivity");
  }
}
