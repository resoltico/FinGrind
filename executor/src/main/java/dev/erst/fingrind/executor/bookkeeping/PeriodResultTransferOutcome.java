package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Sealed family of period-result-transfer administration outcomes. */
public sealed interface PeriodResultTransferOutcome
    permits PeriodResultTransferOutcome.Transferred, PeriodResultTransferOutcome.Rejected {

  /** Successful durable period-result-transfer outcome carrying the stored transfer fact. */
  record Transferred(TransferredPeriodResult transferredPeriodResult)
      implements PeriodResultTransferOutcome {
    public Transferred {
      Objects.requireNonNull(transferredPeriodResult, "transferredPeriodResult");
    }
  }

  /** Deterministic transfer-period-result rejection carrying one administration refusal. */
  record Rejected(BookkeepingAdministrationRejection rejection)
      implements PeriodResultTransferOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
