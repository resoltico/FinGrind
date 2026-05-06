package dev.erst.fingrind.executor.bookkeeping;

import java.time.Instant;
import java.util.Objects;

/** Closed family of bookkeeping outcomes for explicit book initialization. */
public sealed interface BookOpeningOutcome
    permits BookOpeningOutcome.Opened, BookOpeningOutcome.Rejected {
  /** Successful book initialization outcome. */
  record Opened(Instant initializedAt) implements BookOpeningOutcome {
    /** Validates one opened-book outcome. */
    public Opened {
      Objects.requireNonNull(initializedAt, "initializedAt");
    }
  }

  /** Deterministic rejection for explicit book initialization. */
  record Rejected(BookkeepingAdministrationRejection rejection) implements BookOpeningOutcome {
    /** Validates one open-book rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
