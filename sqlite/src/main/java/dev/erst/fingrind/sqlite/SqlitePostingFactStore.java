package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.PostingDraft;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * SQLite-backed book session that keeps one in-process database handle per opened book.
 *
 * <p>This session is thread-confined. One CLI command owns one instance and uses it on one thread.
 */
final class SqlitePostingFactStore extends SqliteStoreContext {

  /** Opens one SQLite-backed book boundary without mutating storage eagerly. */
  SqlitePostingFactStore(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    super(bookPath, bookPassphrase);
  }

  /** Opens one SQLite-backed book boundary with the selected storage access mode. */
  SqlitePostingFactStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    super(bookPath, bookPassphrase, accessMode);
  }

  SqlitePostingFactStore(BookAccess bookAccess) {
    super(bookAccess, SqliteStoreAccessMode.READ_WRITE_CREATE);
  }

  SqlitePostingFactStore(BookAccess bookAccess, SqliteStoreAccessMode accessMode) {
    super(bookAccess, accessMode);
  }

  SqlitePostingFactStore(
      BookAccess bookAccess,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier) {
    super(bookAccess, accessMode, sqliteApiSupplier);
  }

  /** Opens and primes one SQLite-backed book session for explicit CLI/workflow result handling. */
  static ContractDecision<SqlitePostingFactStore> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    return SqliteStoreOpening.openResolved(bookPath, bookPassphrase, accessMode);
  }

  /** Commits one fully materialized posting fact for fixture-oriented callers. */
  PostingCommitResult commit(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return commit(
        new PostingDraft(
            postingFact.journalEntry(), postingFact.postingLineage(), postingFact.provenance()),
        postingFact::postingId);
  }

  static ContractDecision<SqliteBookPassphrase> passphraseFor(BookAccess bookAccess) {
    return passphraseDecisionFor(bookAccess);
  }

  static ContractDecision<SqliteBookPassphrase> passphraseDecisionFor(BookAccess bookAccess) {
    return SqliteStoreOperations.passphraseFor(bookAccess);
  }

  /** Returns the active native database handle when one has already been opened. */
  SqliteNativeDatabase activeNativeDatabase() {
    return database().nativeDatabase();
  }
}
