package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;

/** Public factory for SQLite-backed reporting-period-close sessions. */
public final class SqliteReportingPeriodCloseSessions {
  private SqliteReportingPeriodCloseSessions() {}

  /** Opens a reporting-period-close session from logical book access metadata. */
  public static SqliteReportingPeriodCloseSession open(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolved(bookAccess, passphraseResolver, passphraseIntent).requireAccepted();
  }

  /** Resolves a reporting-period-close session from logical book access metadata. */
  public static ContractDecision<SqliteReportingPeriodCloseSession> openResolved(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            passphraseIntent),
        SqliteCapabilitySessions::reportingPeriodClose);
  }
}
