package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Public period-result-transfer fact carrying the generated transfer posting identities. */
public record TransferredPeriodResult(
    int transferOrder,
    ReportingPeriod reportingPeriod,
    AccountCode resultHoldingAccountCode,
    List<CurrencyBalance> transferredTotals,
    Instant transferredAt,
    List<PostingId> transferPostingIds) {
  /** Validates one published period-result-transfer fact. */
  public TransferredPeriodResult {
    if (transferOrder < 1) {
      throw new IllegalArgumentException("transferOrder must be at least one.");
    }
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(resultHoldingAccountCode, "resultHoldingAccountCode");
    transferredTotals =
        ContractDescriptorValidation.copyList(transferredTotals, "transferredTotals");
    Objects.requireNonNull(transferredAt, "transferredAt");
    transferPostingIds =
        ContractDescriptorValidation.copyList(transferPostingIds, "transferPostingIds");
  }
}
