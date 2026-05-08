package dev.erst.fingrind.executor.bookkeeping.posting;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.time.LocalDate;
import java.util.Objects;

/** Local preflight outcome before public posting-result projection. */
public sealed interface PostingPreflightOutcome
    permits PostingPreflightOutcome.Accepted, PostingPreflightOutcome.Rejected {
  /** Successful local preflight outcome. */
  record Accepted(IdempotencyKey idempotencyKey, LocalDate effectiveDate)
      implements PostingPreflightOutcome {
    public Accepted {
      Objects.requireNonNull(idempotencyKey, "idempotencyKey");
      Objects.requireNonNull(effectiveDate, "effectiveDate");
    }
  }

  /** Deterministic local preflight rejection. */
  record Rejected(BookkeepingPostingRejection rejection) implements PostingPreflightOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
