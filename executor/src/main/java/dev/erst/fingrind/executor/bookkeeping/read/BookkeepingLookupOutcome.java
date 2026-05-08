package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import java.util.Objects;

/** Local bookkeeping lookup result that preserves rejection, absence, and presence distinctly. */
public sealed interface BookkeepingLookupOutcome<T>
    permits BookkeepingLookupOutcome.Found,
        BookkeepingLookupOutcome.Missing,
        BookkeepingLookupOutcome.Rejected {
  /** Successful lookup result carrying the matched value. */
  record Found<T>(T value) implements BookkeepingLookupOutcome<T> {
    public Found {
      Objects.requireNonNull(value, "value");
    }
  }

  /** Lookup result that completed against an initialized book without a matching value. */
  record Missing<T>() implements BookkeepingLookupOutcome<T> {}

  /** Lookup result rejected before the lookup could run safely. */
  record Rejected<T>(BookkeepingQueryRejection rejection) implements BookkeepingLookupOutcome<T> {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
