package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/** Shared store-opening seam for SQLite-backed workflow sessions. */
public final class SqliteBookSessions {
  private SqliteBookSessions() {}

  static <T> ContractDecision<T> project(
      ContractDecision<SqlitePostingFactStore> decision,
      Function<SqlitePostingFactStore, T> projector) {
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(projector, "projector");
    return decision.fold(
        accepted -> ContractDecision.accepted(projector.apply(accepted)),
        ContractDecision::rejected);
  }

  /** Opens a posting-fact store against a filesystem book with create-if-missing access. */
  public static SqlitePostingFactStore openStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return openStore(bookPath, bookPassphrase, SqliteBookSessionMode.READ_WRITE_CREATE);
  }

  /** Opens a posting-fact store against a filesystem book with the requested session mode. */
  public static SqlitePostingFactStore openStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return openResolvedStore(bookPath, bookPassphrase, sessionMode).requireAccepted();
  }

  /** Resolves a posting-fact store against a filesystem book without forcing acceptance. */
  public static ContractDecision<SqlitePostingFactStore> openResolvedStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteBookSessionMode sessionMode) {
    return SqlitePostingFactStore.openResolved(
            bookPath,
            bookPassphrase,
            toStoreAccessMode(Objects.requireNonNull(sessionMode, "sessionMode")))
        .fold(ContractDecision::accepted, ContractDecision::rejected);
  }

  /** Opens a posting-fact store from logical book access metadata and a passphrase resolver. */
  public static SqlitePostingFactStore openStore(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedStore(bookAccess, sessionMode, passphraseResolver, passphraseIntent)
        .requireAccepted();
  }

  /** Resolves a posting-fact store from logical book access metadata without forcing acceptance. */
  public static ContractDecision<SqlitePostingFactStore> openResolvedStore(
      BookAccess bookAccess,
      SqliteBookSessionMode sessionMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedStore(
        bookAccess,
        toStoreAccessMode(Objects.requireNonNull(sessionMode, "sessionMode")),
        passphraseResolver,
        passphraseIntent);
  }

  /** Resolves a new-book session that atomically refuses an existing destination. */
  static ContractDecision<SqlitePostingFactStore> openNewBookResolved(
      BookAccess bookAccess,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    return openResolvedStore(
        bookAccess,
        SqliteStoreAccessMode.READ_WRITE_CREATE_EXCLUSIVE,
        passphraseResolver,
        passphraseIntent);
  }

  private static ContractDecision<SqlitePostingFactStore> openResolvedStore(
      BookAccess bookAccess,
      SqliteStoreAccessMode accessMode,
      SqlitePassphraseResolver passphraseResolver,
      SqlitePassphraseIntent passphraseIntent) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(accessMode, "accessMode");
    Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    Objects.requireNonNull(passphraseIntent, "passphraseIntent");
    return passphraseResolver
        .resolve(bookAccess, passphraseIntent)
        .fold(
            bookPassphrase ->
                SqlitePostingFactStore.openResolved(
                    bookAccess.bookFilePath(), bookPassphrase, accessMode),
            ContractDecision::rejected);
  }

  private static SqliteStoreAccessMode toStoreAccessMode(SqliteBookSessionMode sessionMode) {
    return switch (sessionMode) {
      case READ_ONLY -> SqliteStoreAccessMode.READ_ONLY;
      case PLAN_READ_ONLY -> SqliteStoreAccessMode.PLAN_READ_ONLY;
      case READ_WRITE_EXISTING -> SqliteStoreAccessMode.READ_WRITE_EXISTING;
      case READ_WRITE_CREATE -> SqliteStoreAccessMode.READ_WRITE_CREATE;
      case PLAN_EXECUTION -> SqliteStoreAccessMode.PLAN_EXECUTION;
    };
  }
}
