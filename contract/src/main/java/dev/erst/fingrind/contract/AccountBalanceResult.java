package dev.erst.fingrind.contract;

import java.util.Objects;

/** Closed result family for account-balance queries. */
public sealed interface AccountBalanceResult
    permits AccountBalanceResult.Reported, AccountBalanceResult.Rejected {

  /** Success result carrying one computed account-balance snapshot. */
  record Reported(AccountBalanceSnapshot snapshot) implements AccountBalanceResult {
    /** Validates the account-balance payload. */
    public Reported {
      Objects.requireNonNull(snapshot, "snapshot");
    }
  }

  /** Deterministic refusal for account-balance queries. */
  record Rejected(BookQueryRejection rejection) implements AccountBalanceResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
