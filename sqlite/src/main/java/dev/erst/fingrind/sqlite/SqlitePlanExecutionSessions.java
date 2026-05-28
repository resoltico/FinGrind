package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;

/** Public factory for SQLite-backed ledger-plan execution sessions. */
public final class SqlitePlanExecutionSessions {
  private SqlitePlanExecutionSessions() {}

  /** Opens a ledger-plan execution session from logical book access metadata. */
  public static SqlitePlanExecutionSession open(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolved(bookAccess, passphraseResolver, passphraseIntent).requireAccepted();
  }

  /** Resolves a ledger-plan execution session from logical book access metadata. */
  public static ContractDecision<SqlitePlanExecutionSession> openResolved(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(
            bookAccess, SqliteBookSessionMode.PLAN_EXECUTION, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::planExecution);
  }
}
