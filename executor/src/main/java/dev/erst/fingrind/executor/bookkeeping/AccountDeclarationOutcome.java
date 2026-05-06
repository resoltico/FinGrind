package dev.erst.fingrind.executor.bookkeeping;

import java.util.Objects;

/** Closed family of bookkeeping outcomes for one account declaration attempt. */
public sealed interface AccountDeclarationOutcome
    permits AccountDeclarationOutcome.Declared, AccountDeclarationOutcome.Rejected {
  /** Successful account declaration or reactivation outcome. */
  record Declared(RegisteredAccount account) implements AccountDeclarationOutcome {
    /** Validates one declared-account outcome. */
    public Declared {
      Objects.requireNonNull(account, "account");
    }
  }

  /** Deterministic bookkeeping rejection for account declaration. */
  record Rejected(BookkeepingAdministrationRejection rejection)
      implements AccountDeclarationOutcome {
    /** Validates one account-declaration rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
