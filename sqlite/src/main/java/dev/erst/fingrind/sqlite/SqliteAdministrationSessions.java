package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;

/** Public factory for SQLite-backed administration sessions. */
public final class SqliteAdministrationSessions {
  private SqliteAdministrationSessions() {}

  /** Opens an administration session against a filesystem book with create-if-missing access. */
  public static SqliteAdministrationSession open(
      Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return open(bookPath, bookPassphrase, SqliteBookSessionMode.READ_WRITE_CREATE);
  }

  /** Opens an administration session against a filesystem book with the requested session mode. */
  public static SqliteAdministrationSession open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return openResolved(bookPath, bookPassphrase, sessionMode).requireAccepted();
  }

  /** Resolves an administration session against a filesystem book without forcing acceptance. */
  public static ContractDecision<SqliteAdministrationSession> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(bookPath, bookPassphrase, sessionMode),
        SqliteCapabilitySessions::administration);
  }

  /**
   * Opens an administration session from logical book access metadata and a passphrase resolver.
   */
  public static SqliteAdministrationSession open(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolved(bookAccess, sessionMode, passphraseResolver, passphraseIntent)
        .requireAccepted();
  }

  /**
   * Resolves an administration session from logical book access metadata without forcing
   * acceptance.
   */
  public static ContractDecision<SqliteAdministrationSession> openResolved(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(
            bookAccess, sessionMode, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::administration);
  }

  /** Resolves one administration session that creates only an absent new book destination. */
  public static ContractDecision<SqliteAdministrationSession> openNewBookResolved(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openNewBookResolved(bookAccess, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::administration);
  }
}
