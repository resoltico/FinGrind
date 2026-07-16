package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Internal SQLite-backed book session core that keeps one in-process database handle per opened
 * book.
 *
 * <p>This core is thread-confined. One CLI command owns one instance and uses it on one thread.
 */
class SqlitePostingFactStore
    implements SqlitePostingFactStoreReadView,
        SqliteInventoryValuationReadOperationsView,
        SqlitePostingFactStoreMutationView,
        SqlitePostingFactStoreLifecycleView {
  private final SqliteThreadOwner threadOwner = new SqliteThreadOwner("SQLite book session");
  private final SqliteStoreContext context;
  final SqliteSessionSecret sessionSecret;
  final SqliteStoreLifecycle lifecycle;
  private final SqliteStoreReadOperations readOperations;
  private final SqliteInventoryValuationReadOperations inventoryValuationReadOperations;
  private final SqliteStoreMutationOperations mutationOperations;

  /** Opens one SQLite-backed book boundary without mutating storage eagerly. */
  SqlitePostingFactStore(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    this(bookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE);
  }

  /** Opens one SQLite-backed book boundary with the selected storage access mode. */
  SqlitePostingFactStore(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    this(bookPath, bookPassphrase, accessMode, SqliteNativeBootstrap::api);
  }

  SqlitePostingFactStore(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier) {
    this(
        bookPath,
        bookPassphrase,
        accessMode,
        sqliteApiSupplier,
        SqliteCommitFaultHook.NONE,
        PostingAcceptancePolicy.currentKernel());
  }

  SqlitePostingFactStore(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier,
      SqliteCommitFaultHook commitFaultHook) {
    this(
        bookPath,
        bookPassphrase,
        accessMode,
        sqliteApiSupplier,
        commitFaultHook,
        PostingAcceptancePolicy.currentKernel());
  }

  SqlitePostingFactStore(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteStoreAccessMode accessMode,
      Supplier<SqliteNativeApi> sqliteApiSupplier,
      SqliteCommitFaultHook commitFaultHook,
      PostingAcceptancePolicy postingAcceptancePolicy) {
    this.context =
        new SqliteStoreContext(
            Objects.requireNonNull(bookPath, "bookPath"),
            Objects.requireNonNull(accessMode, "accessMode"),
            Objects.requireNonNull(sqliteApiSupplier, "sqliteApiSupplier"));
    this.sessionSecret =
        new SqliteSessionSecret(Objects.requireNonNull(bookPassphrase, "bookPassphrase"));
    this.lifecycle = new SqliteStoreLifecycle(this.context, sessionSecret);
    this.readOperations = new SqliteStoreReadOperations(context, lifecycle);
    this.inventoryValuationReadOperations =
        new SqliteInventoryValuationReadOperations(context, lifecycle);
    this.mutationOperations =
        new SqliteStoreMutationOperations(
            context,
            lifecycle,
            Objects.requireNonNull(commitFaultHook, "commitFaultHook"),
            Objects.requireNonNull(postingAcceptancePolicy, "postingAcceptancePolicy"));
  }

  /** Opens and primes one SQLite-backed book session for explicit CLI/workflow result handling. */
  static ContractDecision<SqlitePostingFactStore> openResolved(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
    return SqliteStoreOpening.openResolved(bookPath, bookPassphrase, accessMode);
  }

  @Override
  public SqliteThreadOwner storeThreadOwner() {
    return threadOwner;
  }

  @Override
  public SqliteStoreReadOperations storeReadOperations() {
    return readOperations;
  }

  @Override
  public SqliteInventoryValuationReadOperations storeInventoryValuationReadOperations() {
    return inventoryValuationReadOperations;
  }

  @Override
  public SqliteStoreMutationOperations storeMutationOperations() {
    return mutationOperations;
  }

  @Override
  public SqliteStoreLifecycle storeLifecycle() {
    return lifecycle;
  }

  @Override
  public SqliteStoreContext storeContext() {
    return context;
  }
}
