package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Store-ready interim-result-sweep draft containing every generated durable closing posting. */
public record InterimResultSweepDraft(
    ReportingPeriod reportingPeriod,
    AccountCode resultHoldingAccountCode,
    List<CurrencyBalance> sweptTotals,
    Instant sweptAt,
    List<PostingDraft> closingPostings) {
  /** Validates one interim-result-sweep draft before durable persistence. */
  public InterimResultSweepDraft {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    Objects.requireNonNull(sweptTotals, "sweptTotals");
    Objects.requireNonNull(sweptAt, "sweptAt");
    Objects.requireNonNull(closingPostings, "closingPostings");
    sweptTotals = List.copyOf(sweptTotals);
    closingPostings = List.copyOf(closingPostings);
  }
}
