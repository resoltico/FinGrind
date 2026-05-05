package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.BookAdministrationSession;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import java.time.Instant;
import java.util.Objects;

/** Narrow administration-session view over one SQLite-backed store. */
final class SqliteBookAdministrationSessionView implements BookAdministrationSession {
  private final SqliteStoreMutationOperations mutationOperations;

  SqliteBookAdministrationSessionView(SqliteStoreMutationOperations mutationOperations) {
    this.mutationOperations = Objects.requireNonNull(mutationOperations, "mutationOperations");
  }

  @Override
  public BookOpeningOutcome openBook(Instant initializedAt) {
    return mutationOperations.openBook(initializedAt);
  }

  @Override
  public AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    return mutationOperations.declareAccount(accountCode, accountName, normalBalance, declaredAt);
  }
}
