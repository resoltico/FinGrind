package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One durably recorded interim-result sweep with its generated sweep postings. */
public record SweptInterimResult(
    int sweepOrder,
    ReportingPeriod reportingPeriod,
    AccountCode resultHoldingAccountCode,
    List<CurrencyBalance> sweptTotals,
    Instant sweptAt,
    List<PostingId> sweepPostingIds) {
  /** Validates one durably recorded interim-result sweep. */
  public SweptInterimResult {
    if (sweepOrder < 1) {
      throw new IllegalArgumentException("sweepOrder must be at least one.");
    }
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    Objects.requireNonNull(sweptTotals, "sweptTotals");
    Objects.requireNonNull(sweptAt, "sweptAt");
    Objects.requireNonNull(sweepPostingIds, "sweepPostingIds");
    sweptTotals = List.copyOf(sweptTotals);
    sweepPostingIds = List.copyOf(sweepPostingIds);
  }
}
