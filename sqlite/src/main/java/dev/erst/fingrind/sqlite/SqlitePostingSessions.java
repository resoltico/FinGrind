package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;

/** Public factory for SQLite-backed posting sessions. */
public final class SqlitePostingSessions {
  private SqlitePostingSessions() {}

  /** Opens a posting session against a filesystem book with create-if-missing access. */
  public static SqlitePostingSession open(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return open(bookPath, bookPassphrase, SqliteBookSessionMode.READ_WRITE_CREATE);
  }

  /** Opens a posting session against a filesystem book with the requested session mode. */
  public static SqlitePostingSession open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return openResolved(bookPath, bookPassphrase, sessionMode).requireAccepted();
  }

  /** Resolves a posting session against a filesystem book without forcing acceptance. */
  public static ContractDecision<SqlitePostingSession> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(bookPath, bookPassphrase, sessionMode),
        SqliteCapabilitySessions::posting);
  }

  /** Opens a posting session from logical book access metadata and a passphrase resolver. */
  public static SqlitePostingSession open(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolved(bookAccess, sessionMode, passphraseResolver, passphraseIntent)
        .requireAccepted();
  }

  /** Resolves a posting session from logical book access metadata without forcing acceptance. */
  public static ContractDecision<SqlitePostingSession> openResolved(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return SqliteBookSessions.project(
        SqliteBookSessions.openResolvedStore(
            bookAccess, sessionMode, passphraseResolver, passphraseIntent),
        SqliteCapabilitySessions::posting);
  }
}
