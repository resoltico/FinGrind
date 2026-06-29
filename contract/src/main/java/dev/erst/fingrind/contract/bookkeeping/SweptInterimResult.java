package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Public interim-result-sweep fact carrying the generated sweep posting identities. */
public record SweptInterimResult(
    int sweepOrder,
    ReportingPeriod reportingPeriod,
    AccountCode resultHoldingAccountCode,
    List<CurrencyBalance> sweptTotals,
    Instant sweptAt,
    List<PostingId> sweepPostingIds) {
  /** Validates one published interim-result-sweep fact. */
  public SweptInterimResult {
    if (sweepOrder < 1) {
      throw new IllegalArgumentException("sweepOrder must be at least one.");
    }
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    sweptTotals = ContractDescriptorValidation.copyList(sweptTotals, "sweptTotals");
    Objects.requireNonNull(sweptAt, "sweptAt");
    sweepPostingIds = ContractDescriptorValidation.copyList(sweepPostingIds, "sweepPostingIds");
  }
}
