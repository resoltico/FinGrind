package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.BookQueryRejection;
import java.util.Objects;

/** Local executor outcome for one read-side bookkeeping query before public result projection. */
sealed interface BookReadOutcome<T> permits BookReadOutcome.Reported, BookReadOutcome.Rejected {
  /** Successful local outcome carrying the bookkeeping view. */
  record Reported<T>(T value) implements BookReadOutcome<T> {
    public Reported {
      Objects.requireNonNull(value, "value");
    }
  }

  /** Deterministic local rejection carrying the canonical query refusal. */
  record Rejected<T>(BookQueryRejection rejection) implements BookReadOutcome<T> {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
