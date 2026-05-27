package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseResolver;

/** Public factory for SQLite-backed period-result-transfer sessions. */
public final class SqlitePeriodResultTransferSessions {
  private SqlitePeriodResultTransferSessions() {}

  /** Opens a period-result-transfer session from logical book access metadata. */
  public static SqlitePeriodResultTransferSession open(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolved(bookAccess, passphraseResolver, passphraseIntent).requireAccepted();
  }

  /** Resolves a period-result-transfer session from logical book access metadata. */
  public static ContractDecision<SqlitePeriodResultTransferSession> openResolved(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            passphraseIntent),
        SqliteCapabilitySessions::periodResultTransfer);
  }
}
