package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Sealed family of public period-result-transfer outcomes. */
public sealed interface PeriodResultTransferResult
    permits PeriodResultTransferResult.Transferred, PeriodResultTransferResult.Rejected {

  /** Successful transfer-period-result outcome carrying the durable transferred-period fact. */
  record Transferred(TransferredPeriodResult transferredPeriodResult)
      implements PeriodResultTransferResult {
    public Transferred {
      Objects.requireNonNull(transferredPeriodResult, "transferredPeriodResult");
    }
  }

  /** Period-result-transfer outcome carrying one deterministic administration rejection. */
  record Rejected(BookAdministrationRejection rejection) implements PeriodResultTransferResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
