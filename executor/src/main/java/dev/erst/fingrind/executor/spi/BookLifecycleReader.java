package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.BookIdentity;

/** Reads one selected book's lifecycle state without mutating it. */
@FunctionalInterface
public interface BookLifecycleReader {
  /** Returns one local lifecycle snapshot for the selected book. */
  BookLifecycleInspection inspectBook();

  /** Returns whether initialized-book workflows may proceed for the selected book. */
  default boolean allowsInitializedWorkflow() {
    return inspectBook().allowsInitializedWorkflow();
  }

  /** Returns the selected initialized book identity or throws when the book is not initialized. */
  default BookIdentity requireInitializedBookIdentity() {
    return BookLifecycleInspection.requireInitializedBookIdentity(inspectBook());
  }
}
