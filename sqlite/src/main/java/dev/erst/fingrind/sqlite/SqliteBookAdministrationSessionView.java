package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.BookAdministrationSession;
import java.time.Instant;
import java.util.Objects;

/** Narrow administration-session view over one SQLite-backed store. */
final class SqliteBookAdministrationSessionView implements BookAdministrationSession {
  private final SqliteStoreContext store;

  SqliteBookAdministrationSessionView(SqliteStoreContext store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  @Override
  public dev.erst.fingrind.contract.OpenBookResult openBook(Instant initializedAt) {
    return store.openBook(initializedAt);
  }

  @Override
  public dev.erst.fingrind.contract.DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    return store.declareAccount(accountCode, accountName, normalBalance, declaredAt);
  }
}
