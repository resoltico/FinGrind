package dev.erst.fingrind.application;

import java.util.Objects;

/** Closed result family for account declaration or reactivation. */
public sealed interface DeclareAccountResult
    permits DeclareAccountResult.Declared, DeclareAccountResult.Rejected {

  /** Success result carrying the durable declared-account snapshot. */
  record Declared(DeclaredAccount account) implements DeclareAccountResult {
    /** Validates the declared account. */
    public Declared {
      Objects.requireNonNull(account, "account");
    }
  }

  /** Deterministic refusal for declare-account. */
  record Rejected(BookAdministrationRejection rejection) implements DeclareAccountResult {
    /** Validates the deterministic rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
