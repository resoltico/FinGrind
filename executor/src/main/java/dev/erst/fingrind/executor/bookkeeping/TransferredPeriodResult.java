package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One durably recorded period-result transfer with its generated transfer postings. */
public record TransferredPeriodResult(
    int transferOrder,
    ReportingPeriod reportingPeriod,
    AccountCode resultHoldingAccountCode,
    List<CurrencyBalance> transferredTotals,
    Instant transferredAt,
    List<PostingId> transferPostingIds) {
  /** Validates one durably recorded period-result transfer. */
  public TransferredPeriodResult {
    if (transferOrder < 1) {
      throw new IllegalArgumentException("transferOrder must be at least one.");
    }
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    Objects.requireNonNull(transferredTotals, "transferredTotals");
    Objects.requireNonNull(transferredAt, "transferredAt");
    Objects.requireNonNull(transferPostingIds, "transferPostingIds");
    transferredTotals = List.copyOf(transferredTotals);
    transferPostingIds = List.copyOf(transferPostingIds);
  }
}
