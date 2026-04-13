package dev.erst.fingrind.application;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.NormalBalance;
import java.util.Objects;

/** Closed family of deterministic book-administration refusals. */
public sealed interface BookAdministrationRejection
    permits BookAdministrationRejection.BookAlreadyInitialized,
        BookAdministrationRejection.BookNotInitialized,
        BookAdministrationRejection.BookContainsSchema,
        BookAdministrationRejection.NormalBalanceConflict {

  /** Rejection for an explicit open-book request against an already initialized book. */
  record BookAlreadyInitialized() implements BookAdministrationRejection {}

  /** Rejection for commands that require an initialized book but found none. */
  record BookNotInitialized() implements BookAdministrationRejection {}

  /** Rejection for open-book against a pre-existing SQLite file that is not empty. */
  record BookContainsSchema() implements BookAdministrationRejection {}

  /** Rejection for redeclaring an account with a different immutable normal balance. */
  record NormalBalanceConflict(
      AccountCode accountCode,
      NormalBalance existingNormalBalance,
      NormalBalance requestedNormalBalance)
      implements BookAdministrationRejection {
    /** Validates the conflicting account definition. */
    public NormalBalanceConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(existingNormalBalance, "existingNormalBalance");
      Objects.requireNonNull(requestedNormalBalance, "requestedNormalBalance");
    }
  }
}
