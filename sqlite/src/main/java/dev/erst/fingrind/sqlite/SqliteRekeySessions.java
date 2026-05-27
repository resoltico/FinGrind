package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseResolver;

/** Public factory for SQLite-backed rekey sessions. */
public final class SqliteRekeySessions {
  private SqliteRekeySessions() {}

  /** Opens a rekey session from logical book access metadata. */
  public static SqliteRekeySession open(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolved(bookAccess, passphraseResolver, passphraseIntent).requireAccepted();
  }

  /** Resolves a rekey session from logical book access metadata. */
  public static ContractDecision<SqliteRekeySession> openResolved(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_EXISTING,
            passphraseResolver,
            passphraseIntent),
        SqliteCapabilitySessions::rekey);
  }
}
