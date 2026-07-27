package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Pre-persistence Account Registry decision for one retirement request. */
public sealed interface AccountRetirementDecision
    permits AccountRetirementDecision.Retired,
        AccountRetirementDecision.Unchanged,
        AccountRetirementDecision.Rejected {
  /** The active account should be persisted as retired. */
  record Retired(RegisteredAccount account) implements AccountRetirementDecision {
    public Retired {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The account is already retired. */
  record Unchanged(RegisteredAccount account) implements AccountRetirementDecision {
    public Unchanged {
      Objects.requireNonNull(account, "account");
    }
  }

  /** The Account Registry rejected retirement before persistence. */
  record Rejected(BookkeepingAdministrationRejection rejection)
      implements AccountRetirementDecision {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
