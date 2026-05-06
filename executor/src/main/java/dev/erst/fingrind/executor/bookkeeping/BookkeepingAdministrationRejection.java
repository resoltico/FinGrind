package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.NormalBalance;
import java.util.Objects;

/** Local bookkeeping refusal family for book initialization and account declaration. */
public sealed interface BookkeepingAdministrationRejection
    permits BookkeepingAdministrationRejection.BookAlreadyInitialized,
        BookkeepingAdministrationRejection.BookNotInitialized,
        BookkeepingAdministrationRejection.BookContainsSchema,
        BookkeepingAdministrationRejection.NormalBalanceConflict {

  /** Refusal for an explicit open-book request against an initialized book. */
  record BookAlreadyInitialized() implements BookkeepingAdministrationRejection {}

  /** Refusal for a bookkeeping command that requires an initialized book. */
  record BookNotInitialized() implements BookkeepingAdministrationRejection {}

  /** Refusal for open-book against a pre-existing SQLite file with schema objects. */
  record BookContainsSchema() implements BookkeepingAdministrationRejection {}

  /** Refusal for redeclaring an account with a conflicting immutable normal balance. */
  record NormalBalanceConflict(
      AccountCode accountCode,
      NormalBalance existingNormalBalance,
      NormalBalance requestedNormalBalance)
      implements BookkeepingAdministrationRejection {
    public NormalBalanceConflict {
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(existingNormalBalance, "existingNormalBalance");
      Objects.requireNonNull(requestedNormalBalance, "requestedNormalBalance");
    }
  }
}
