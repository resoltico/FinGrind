package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Closed result family for an account retirement. */
public sealed interface RetireAccountResult
    permits RetireAccountResult.Retired,
        RetireAccountResult.Unchanged,
        RetireAccountResult.Rejected {
  /** Success result carrying the retired account snapshot. */
  record Retired(DeclaredAccount account) implements RetireAccountResult {
    public Retired {
      Objects.requireNonNull(account, "account");
    }
  }

  /** Successful no-op result when the account is already retired. */
  record Unchanged(DeclaredAccount account) implements RetireAccountResult {
    public Unchanged {
      Objects.requireNonNull(account, "account");
    }
  }

  /** Deterministic refusal for retire-account. */
  record Rejected(BookAdministrationRejection rejection) implements RetireAccountResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
