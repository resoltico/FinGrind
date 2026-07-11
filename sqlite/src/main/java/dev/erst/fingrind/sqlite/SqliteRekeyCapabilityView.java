package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.time.Instant;
import java.util.Objects;

/** Shared rekey delegation defaults for SQLite capability wrappers. */
interface SqliteRekeyCapabilityView extends SqliteRekeySession {
  /** Opens the package-private capability implementation behind the public rekey session view. */
  static SqliteRekeySession open(SqlitePostingFactStore store) {
    SqlitePostingFactStore checkedStore = Objects.requireNonNull(store, "store");
    return new SqliteRekeyCapabilityView() {
      @Override
      public SqliteStoreMutationOperations storeMutationOperations() {
        return checkedStore.storeMutationOperations();
      }

      @Override
      public java.nio.file.Path storeBookPath() {
        return checkedStore.storeBookPath();
      }

      @Override
      public void close() {
        checkedStore.close();
      }
    };
  }

  /** Returns the mutation operations owner for the underlying SQLite store. */
  SqliteStoreMutationOperations storeMutationOperations();

  /** Returns the protected-book path owned by the underlying SQLite store. */
  java.nio.file.Path storeBookPath();

  @Override
  default ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver,
      Instant rekeyedAt) {
    return ContractDecision.accepted(
        storeMutationOperations()
            .rekeyBook(
                passphraseResolver
                    .resolve(
                        storeBookPath(),
                        replacementPassphraseSource,
                        SqlitePassphraseIntent.NEW_SECRET)
                    .requireAccepted(),
                rekeyedAt));
  }
}
