package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Store-ready transfer-period-result draft containing every generated durable closing posting. */
public record PeriodResultTransferDraft(
    ReportingPeriod reportingPeriod,
    AccountCode resultHoldingAccountCode,
    List<CurrencyBalance> transferredTotals,
    Instant transferredAt,
    List<PostingDraft> closingPostings) {
  /** Validates one transfer-period-result draft before durable persistence. */
  public PeriodResultTransferDraft {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    Objects.requireNonNull(transferredTotals, "transferredTotals");
    Objects.requireNonNull(transferredAt, "transferredAt");
    Objects.requireNonNull(closingPostings, "closingPostings");
    transferredTotals = List.copyOf(transferredTotals);
    closingPostings = List.copyOf(closingPostings);
  }
}
