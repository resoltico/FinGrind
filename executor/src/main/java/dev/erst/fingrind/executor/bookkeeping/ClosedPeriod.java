package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One durably recorded closed reporting period with its generated closing postings. */
public record ClosedPeriod(
    int closeOrder,
    ReportingPeriod reportingPeriod,
    AccountCode retainedEarningsAccountCode,
    List<CurrencyBalance> closedTotals,
    Instant closedAt,
    List<PostingId> closingPostingIds) {
  /** Validates one durably recorded closed reporting period. */
  public ClosedPeriod {
    if (closeOrder < 1) {
      throw new IllegalArgumentException("closeOrder must be at least one.");
    }
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(retainedEarningsAccountCode, "retainedEarningsAccountCode");
    Objects.requireNonNull(closedTotals, "closedTotals");
    Objects.requireNonNull(closedAt, "closedAt");
    Objects.requireNonNull(closingPostingIds, "closingPostingIds");
    closedTotals = List.copyOf(closedTotals);
    closingPostingIds = List.copyOf(closingPostingIds);
  }
}
