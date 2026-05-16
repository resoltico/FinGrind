package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Store-ready close-period draft containing every generated durable closing posting. */
public record PeriodCloseDraft(
    ReportingPeriod reportingPeriod,
    AccountCode closingEquityAccountCode,
    List<CurrencyBalance> closedTotals,
    Instant closedAt,
    List<PostingDraft> closingPostings) {
  /** Validates one close-period draft before durable persistence. */
  public PeriodCloseDraft {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(closingEquityAccountCode, "closingEquityAccountCode");
    Objects.requireNonNull(closedTotals, "closedTotals");
    Objects.requireNonNull(closedAt, "closedAt");
    Objects.requireNonNull(closingPostings, "closingPostings");
    closedTotals = List.copyOf(closedTotals);
    closingPostings = List.copyOf(closingPostings);
  }
}
