package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Shared lifecycle gating for bookkeeping read and lookup paths. */
final class BookkeepingReadLifecycleGate {
  private BookkeepingReadLifecycleGate() {}

  static <T> BookkeepingReadOutcome<T> ifInitialized(
      BookkeepingReadStore bookStore, Supplier<BookkeepingReadOutcome<T>> initializedAction) {
    Objects.requireNonNull(bookStore, "bookStore");
    Objects.requireNonNull(initializedAction, "initializedAction");
    if (!bookStore.allowsInitializedWorkflow()) {
      return new BookkeepingReadOutcome.Rejected<>(
          new BookkeepingQueryRejection.BookNotInitialized());
    }
    return initializedAction.get();
  }

  static <T> BookkeepingLookupOutcome<T> lookup(
      BookkeepingReadStore bookStore, Supplier<Optional<T>> initializedAction) {
    Objects.requireNonNull(bookStore, "bookStore");
    Objects.requireNonNull(initializedAction, "initializedAction");
    if (!bookStore.allowsInitializedWorkflow()) {
      return new BookkeepingLookupOutcome.Rejected<>(
          new BookkeepingQueryRejection.BookNotInitialized());
    }
    return initializedAction
        .get()
        .<BookkeepingLookupOutcome<T>>map(BookkeepingLookupOutcome.Found::new)
        .orElseGet(BookkeepingLookupOutcome.Missing::new);
  }
}
