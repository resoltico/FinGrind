package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;

/** Lifecycle defaults backed by the transaction-scoped SQLite validation query owner. */
interface SqliteTransactionValidationLifecycleCapabilityView extends BookLifecycleReader {
  @Override
  default BookLifecycleInspection inspectBook() {
    return SqliteTransactionValidationBook.requireOwner(this).validationQueries().inspectBook();
  }

  @Override
  default boolean allowsInitializedWorkflow() {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .allowsInitializedWorkflow();
  }

  @Override
  default BookIdentity requireInitializedBookIdentity() {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .requireInitializedBookIdentity();
  }
}
