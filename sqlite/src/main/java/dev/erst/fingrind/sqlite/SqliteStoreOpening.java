package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Opens, primes, and transfers ownership of one SQLite posting store. */
enum SqliteStoreOpening {
  INSTANCE;

  static ContractDecision<SqlitePostingFactStore> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    return openResolved(
        bookPath,
        bookPassphrase,
        accessMode,
        SqlitePostingFactStore::new,
        SqliteBestEffort::reportCleanupFailure);
  }

  static ContractDecision<SqlitePostingFactStore> openResolved(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteStoreAccessMode accessMode,
      StoreOpener storeOpener,
      SqliteBestEffort.Reporter cleanupReporter) {
    return INSTANCE.open(bookPath, bookPassphrase, accessMode, storeOpener, cleanupReporter);
  }

  private ContractDecision<SqlitePostingFactStore> open(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteStoreAccessMode accessMode,
      StoreOpener storeOpener,
      SqliteBestEffort.Reporter cleanupReporter) {
    try (OwnershipTransfer opening =
        OwnershipTransfer.open(
            bookPath, bookPassphrase, accessMode, storeOpener, cleanupReporter)) {
      return opening.prime();
    }
  }

  /** Opens one store instance for ownership-transfer priming and cleanup orchestration. */
  @FunctionalInterface
  interface StoreOpener {
    /** Constructs one store with the supplied path, passphrase, and access intent. */
    SqlitePostingFactStore open(
        Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode);
  }

  /** Owns one just-opened store until priming either releases it or closes it on failure. */
  private static final class OwnershipTransfer implements AutoCloseable {
    private final SqliteBestEffort.Reporter cleanupReporter;
    private @Nullable SqlitePostingFactStore bookStore;

    private OwnershipTransfer(
        SqlitePostingFactStore bookStore, SqliteBestEffort.Reporter cleanupReporter) {
      this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
      this.cleanupReporter = Objects.requireNonNull(cleanupReporter, "cleanupReporter");
    }

    static OwnershipTransfer open(
        Path bookPath,
        SqliteBookPassphrase bookPassphrase,
        SqliteStoreAccessMode accessMode,
        StoreOpener storeOpener,
        SqliteBestEffort.Reporter cleanupReporter) {
      return new OwnershipTransfer(
          Objects.requireNonNull(storeOpener, "storeOpener")
              .open(bookPath, bookPassphrase, accessMode),
          cleanupReporter);
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
      try {
        bookStore.close();
      } catch (IllegalStateException exception) {
        cleanupReporter.report("closing one SQLite session during priming handoff", exception);
      }
      bookStore = null;
    }
  }
}
