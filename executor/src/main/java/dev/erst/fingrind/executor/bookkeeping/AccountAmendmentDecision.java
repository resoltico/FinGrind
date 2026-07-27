package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Pre-persistence Account Registry decision for one amendment request. */
public sealed interface AccountAmendmentDecision
    permits AccountAmendmentDecision.Amended,
        AccountAmendmentDecision.Unchanged,
        AccountAmendmentDecision.Rejected {
  /** The requested mutable definition should replace the prior definition. */
  record Amended(RegisteredAccount account) implements AccountAmendmentDecision {
    public Amended {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The requested mutable definition already matches durable state. */
  record Unchanged(RegisteredAccount account) implements AccountAmendmentDecision {
    public Unchanged {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The Account Registry rejected the amendment before persistence. */
  record Rejected(BookkeepingAdministrationRejection rejection)
      implements AccountAmendmentDecision {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
