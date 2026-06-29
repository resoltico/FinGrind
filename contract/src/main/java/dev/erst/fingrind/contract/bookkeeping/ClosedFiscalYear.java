package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Public fiscal-year-close fact carrying the generated close posting identities. */
public record ClosedFiscalYear(
    int closeOrder,
    ReportingPeriod reportingPeriod,
    AccountCode capitalAccountCode,
    AccountCode resultHoldingAccountCode,
    AccountCode retainedAccumulatedAccountCode,
    Instant closedAt,
    List<PostingId> closePostingIds) {
  /** Validates one published fiscal-year-close fact. */
  public ClosedFiscalYear {
    if (closeOrder < 1) {
      throw new IllegalArgumentException("closeOrder must be at least one.");
    }
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(capitalAccountCode, "capitalAccountCode");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    Objects.requireNonNull(retainedAccumulatedAccountCode, "retainedAccumulatedAccountCode");
    Objects.requireNonNull(closedAt, "closedAt");
    closePostingIds = ContractDescriptorValidation.copyList(closePostingIds, "closePostingIds");
  }
}
