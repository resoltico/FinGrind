package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import java.util.Objects;

/** Local bookkeeping read outcome before any public published-language projection. */
public sealed interface BookkeepingReadOutcome<T>
    permits BookkeepingReadOutcome.Reported, BookkeepingReadOutcome.Rejected {
  /** Successful local outcome carrying the bookkeeping view. */
  record Reported<T>(T value) implements BookkeepingReadOutcome<T> {
    public Reported {
      Objects.requireNonNull(value, "value");
    }
  }

  /** Deterministic local rejection carrying the canonical query refusal. */
  record Rejected<T>(BookkeepingQueryRejection rejection) implements BookkeepingReadOutcome<T> {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
