package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Closed result family for an account amendment. */
public sealed interface AmendAccountResult
    permits AmendAccountResult.Amended, AmendAccountResult.Unchanged, AmendAccountResult.Rejected {
  /** Success result carrying the amended account snapshot. */
  record Amended(DeclaredAccount account) implements AmendAccountResult {
    public Amended {
      Objects.requireNonNull(account, "account");
    }
  }

  /** Successful no-op result when the requested definition already matches durable state. */
  record Unchanged(DeclaredAccount account) implements AmendAccountResult {
    public Unchanged {
      Objects.requireNonNull(account, "account");
    }
  }

  /** Deterministic refusal for amend-account. */
  record Rejected(BookAdministrationRejection rejection) implements AmendAccountResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
