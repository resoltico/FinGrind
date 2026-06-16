package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;

/** Shared lifecycle and initialization gate defaults for SQLite capability wrappers. */
interface SqliteLifecycleInspectionCapabilityView
    extends BookLifecycleReader, SqlitePostingFactStoreReadOperationsView {
  /** Returns the lifecycle controller for the underlying SQLite store. */
  SqliteStoreLifecycle storeLifecycle();

  /** Returns the immutable store context metadata. */
  SqliteStoreContext storeContext();

  @Override
  default BookLifecycleInspection inspectBook() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().inspectBook();
  }

  @Override
  default boolean allowsInitializedWorkflow() {
    storeThreadOwner().requireOwnerThread();
    return storeLifecycle().allowsInitializedWorkflow();
  }

  @Override
  default BookIdentity requireInitializedBookIdentity() {
    storeThreadOwner().requireOwnerThread();
    return storeLifecycle().requireInitializedBookIdentity();
  }
}
