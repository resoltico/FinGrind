package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Store-ready fiscal-year-close draft containing every generated durable close posting. */
public record FiscalYearCloseDraft(
    ReportingPeriod reportingPeriod,
    AccountCode capitalAccountCode,
    AccountCode resultHoldingAccountCode,
    AccountCode retainedAccumulatedAccountCode,
    Instant closedAt,
    @Nullable InterimResultSweepDraft unsweptInterimResultSweepDraft,
    List<PostingDraft> closePostingDrafts) {
  /** Validates one fiscal-year-close draft before durable persistence. */
  public FiscalYearCloseDraft {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(capitalAccountCode, "capitalAccountCode");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    Objects.requireNonNull(retainedAccumulatedAccountCode, "retainedAccumulatedAccountCode");
    Objects.requireNonNull(closedAt, "closedAt");
    closePostingDrafts = List.copyOf(closePostingDrafts);
  }
}
