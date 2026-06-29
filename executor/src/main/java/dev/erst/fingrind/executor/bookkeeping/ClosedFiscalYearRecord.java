package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One durably recorded fiscal-year close with its generated close postings. */
public record ClosedFiscalYearRecord(
    int closeOrder,
    ReportingPeriod reportingPeriod,
    AccountCode capitalAccountCode,
    AccountCode resultHoldingAccountCode,
    AccountCode retainedAccumulatedAccountCode,
    Instant closedAt,
    List<PostingId> closePostingIds) {
  /** Validates one durably recorded fiscal-year close. */
  public ClosedFiscalYearRecord {
    if (closeOrder < 1) {
      throw new IllegalArgumentException("closeOrder must be at least one.");
    }
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(capitalAccountCode, "capitalAccountCode");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    Objects.requireNonNull(retainedAccumulatedAccountCode, "retainedAccumulatedAccountCode");
    Objects.requireNonNull(closedAt, "closedAt");
    closePostingIds = List.copyOf(closePostingIds);
  }
}
