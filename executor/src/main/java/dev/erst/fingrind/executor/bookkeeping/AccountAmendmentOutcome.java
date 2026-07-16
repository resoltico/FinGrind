package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Closed family of Account Registry outcomes for one account amendment. */
public sealed interface AccountAmendmentOutcome
    permits AccountAmendmentOutcome.Amended,
        AccountAmendmentOutcome.Unchanged,
        AccountAmendmentOutcome.Rejected {
  /** The requested mutable definition replaced the prior definition. */
  record Amended(RegisteredAccount account) implements AccountAmendmentOutcome {
    public Amended {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The requested mutable definition already matches the durable account. */
  record Unchanged(RegisteredAccount account) implements AccountAmendmentOutcome {
    public Unchanged {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The Account Registry refused the requested amendment. */
  record Rejected(BookkeepingAdministrationRejection rejection) implements AccountAmendmentOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
