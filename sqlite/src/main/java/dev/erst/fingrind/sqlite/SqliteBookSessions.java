package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;
import java.util.Objects;

/** Public factory for opening SQLite-backed FinGrind book sessions. */
public final class SqliteBookSessions {
  private SqliteBookSessions() {}

  /** Opens one SQLite-backed book session using the default create-if-missing intent. */
  public static SqliteBookSession open(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return open(bookPath, bookPassphrase, SqliteBookSessionMode.READ_WRITE_CREATE);
  }

  /** Opens one SQLite-backed book session for the supplied intent. */
  public static SqliteBookSession open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return openResolved(bookPath, bookPassphrase, sessionMode).requireAccepted();
  }

  /** Opens and primes one SQLite-backed book session for explicit result handling. */
  public static ContractDecision<SqliteBookSession> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return SqlitePostingFactStore.openResolved(
            bookPath,
            bookPassphrase,
            toStoreAccessMode(Objects.requireNonNull(sessionMode, "sessionMode")))
        .fold(ContractDecision::accepted, ContractDecision::rejected);
  }

  /** Opens one SQLite-backed book session by resolving the selected contract-level access tuple. */
  public static SqliteBookSession open(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolved(bookAccess, sessionMode, passphraseResolver, passphraseIntent)
        .requireAccepted();
  }

  /** Opens and primes one contract-level SQLite book access tuple for explicit result handling. */
  public static ContractDecision<SqliteBookSession> openResolved(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    Objects.requireNonNull(passphraseIntent, "passphraseIntent");
    return passphraseResolver
        .resolve(bookAccess, passphraseIntent)
        .fold(
            bookPassphrase -> openResolved(bookAccess.bookFilePath(), bookPassphrase, sessionMode),
            ContractDecision::rejected);
  }

  private static SqliteStoreAccessMode toStoreAccessMode(SqliteBookSessionMode sessionMode) {
    return switch (sessionMode) {
      case READ_ONLY -> SqliteStoreAccessMode.READ_ONLY;
      case READ_WRITE_EXISTING -> SqliteStoreAccessMode.READ_WRITE_EXISTING;
      case READ_WRITE_CREATE -> SqliteStoreAccessMode.READ_WRITE_CREATE;
      case PLAN_EXECUTION -> SqliteStoreAccessMode.PLAN_EXECUTION;
    };
  }
}
