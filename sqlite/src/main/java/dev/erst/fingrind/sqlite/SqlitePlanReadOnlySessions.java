package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;

/** Public factory for SQLite-backed credential-free read-only ledger-plan sessions. */
public final class SqlitePlanReadOnlySessions {
  private SqlitePlanReadOnlySessions() {}

  /** Opens a read-only ledger-plan session from logical book access metadata. */
  public static SqlitePlanReadOnlySession open(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolved(bookAccess, passphraseResolver, passphraseIntent).requireAccepted();
  }

  /** Resolves a read-only ledger-plan session from logical book access metadata. */
  public static ContractDecision<SqlitePlanReadOnlySession> openResolved(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(
            bookAccess, SqliteBookSessionMode.PLAN_READ_ONLY, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::planReadOnly);
  }
}
