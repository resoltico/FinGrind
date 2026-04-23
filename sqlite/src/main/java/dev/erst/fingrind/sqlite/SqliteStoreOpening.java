package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.ContractDecision;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Opens, primes, and transfers ownership of one SQLite posting store. */
enum SqliteStoreOpening {
  INSTANCE;

  static ContractDecision<SqlitePostingFactStore> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    return INSTANCE.open(bookPath, bookPassphrase, accessMode);
  }

  private ContractDecision<SqlitePostingFactStore> open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    try (OwnershipTransfer opening = OwnershipTransfer.open(bookPath, bookPassphrase, accessMode)) {
      return opening.prime();
    }
  }

  /** Owns one just-opened store until priming either releases it or closes it on failure. */
  private static final class OwnershipTransfer implements AutoCloseable {
    private @Nullable SqlitePostingFactStore bookStore;

    private OwnershipTransfer(SqlitePostingFactStore bookStore) {
      this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    }

    static OwnershipTransfer open(
        Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
      return new OwnershipTransfer(
          new SqlitePostingFactStore(bookPath, bookPassphrase, accessMode));
    }

    ContractDecision<SqlitePostingFactStore> prime() {
      return store()
          .prime()
          .fold(ignored -> ContractDecision.accepted(release()), ContractDecision::rejected);
    }

    private SqlitePostingFactStore release() {
      SqlitePostingFactStore releasedStore = store();
      bookStore = null;
      return releasedStore;
    }

    private SqlitePostingFactStore store() {
      return Objects.requireNonNull(bookStore, "bookStore");
    }

    @Override
    public void close() {
      if (bookStore == null) {
        return;
      }
      bookStore.close();
      bookStore = null;
    }
  }
}
