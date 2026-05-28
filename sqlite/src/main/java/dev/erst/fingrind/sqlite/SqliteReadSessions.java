package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;

/** Public factory for SQLite-backed read sessions. */
public final class SqliteReadSessions {
  private SqliteReadSessions() {}

  /** Opens a read-only session against a filesystem book. */
  public static SqliteReadSession open(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return openResolved(bookPath, bookPassphrase).requireAccepted();
  }

  /** Resolves a read-only session against a filesystem book without forcing acceptance. */
  public static ContractDecision<SqliteReadSession> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(
            bookPath, bookPassphrase, SqliteBookSessionMode.READ_ONLY),
        SqliteCapabilitySessions::read);
  }

  /** Opens a read-only session from logical book access metadata and a passphrase resolver. */
  public static SqliteReadSession open(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolved(bookAccess, passphraseResolver, passphraseIntent).requireAccepted();
  }

  /** Resolves a read-only session from logical book access metadata without forcing acceptance. */
  public static ContractDecision<SqliteReadSession> openResolved(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(
            bookAccess, SqliteBookSessionMode.READ_ONLY, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::read);
  }
}
