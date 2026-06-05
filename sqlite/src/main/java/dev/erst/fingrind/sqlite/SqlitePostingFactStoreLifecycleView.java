package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;

/** Lifecycle and runtime-handle surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreLifecycleView extends AutoCloseable {
  /** Returns the thread-ownership guard for this store. */
  SqliteThreadOwner storeThreadOwner();

  /** Returns the lifecycle owner for this store. */
  SqliteStoreLifecycle storeLifecycle();

  /** Returns the immutable store context metadata. */
  SqliteStoreContext storeContext();

  /** Opens the underlying database when needed and returns this store as an accepted decision. */
  default ContractDecision<SqlitePostingFactStore> prime() {
    storeThreadOwner().requireOwnerThread();
    return storeLifecycle()
        .prime()
        .fold(
            ignored -> ContractDecision.accepted((SqlitePostingFactStore) this),
            ContractDecision::rejected);
  }

  /** Begins the shared ledger-plan transaction scope for this store. */
  default void beginLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().begin();
  }

  /** Commits the shared ledger-plan transaction scope for this store. */
  default void commitLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().commit();
  }

  /** Rolls back the shared ledger-plan transaction scope for this store. */
  default void rollbackLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().rollback();
  }

  /** Closes the underlying lifecycle and native handles for this store. */
  @Override
  default void close() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().close();
  }

  /** Requires the opened book to be initialized for FinGrind use. */
  default void requireInitializedBook(SqliteNativeDatabase activeDatabase) {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().requireInitializedBook(activeDatabase);
  }

  /** Returns the authoritative protected-book path for this store. */
  default Path bookPath() {
    storeThreadOwner().requireOwnerThread();
    return storeContext().bookPath();
  }

  /** Returns the access mode used to open this store. */
  default SqliteStoreAccessMode accessMode() {
    storeThreadOwner().requireOwnerThread();
    return storeContext().accessMode();
  }

  /** Returns the posting reader owned by this store context. */
  default SqlitePostingReader postingReader() {
    storeThreadOwner().requireOwnerThread();
    return storeContext().postingReader();
  }

  /** Returns the active native database, opening it if required. */
  default SqliteNativeDatabase activeNativeDatabase() {
    storeThreadOwner().requireOwnerThread();
    return storeLifecycle().database();
  }
}
