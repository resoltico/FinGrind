package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Closed family of Account Registry outcomes for one account retirement. */
public sealed interface AccountRetirementOutcome
    permits AccountRetirementOutcome.Retired,
        AccountRetirementOutcome.Unchanged,
        AccountRetirementOutcome.Rejected {
  /** The account was retired from ordinary authored use. */
  record Retired(RegisteredAccount account) implements AccountRetirementOutcome {
    public Retired {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The account was already retired. */
  record Unchanged(RegisteredAccount account) implements AccountRetirementOutcome {
    public Unchanged {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The Account Registry refused the requested retirement. */
  record Rejected(BookkeepingAdministrationRejection rejection)
      implements AccountRetirementOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
