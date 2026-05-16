package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Public closed-period fact carrying the generated closing posting identities. */
public record ClosedPeriod(
    int closeOrder,
    ReportingPeriod reportingPeriod,
    AccountCode closingEquityAccountCode,
    List<CurrencyBalance> closedTotals,
    Instant closedAt,
    List<PostingId> closingPostingIds) {
  /** Validates one published closed-period fact. */
  public ClosedPeriod {
    if (closeOrder < 1) {
      throw new IllegalArgumentException("closeOrder must be at least one.");
    }
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(closingEquityAccountCode, "closingEquityAccountCode");
    closedTotals = ContractDescriptorValidation.copyList(closedTotals, "closedTotals");
    Objects.requireNonNull(closedAt, "closedAt");
    closingPostingIds =
        ContractDescriptorValidation.copyList(closingPostingIds, "closingPostingIds");
  }
}
