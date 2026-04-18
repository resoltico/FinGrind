package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Closed result family for the entry write boundary. */
public sealed interface PostEntryResult permits PreflightEntryResult, CommitEntryResult {

  /** Preflight-only success variant for a validated but not yet committed request. */
  record PreflightAccepted(IdempotencyKey idempotencyKey, LocalDate effectiveDate)
      implements PreflightEntryResult {

    /** Validates the preflight-only success shape. */
    public PreflightAccepted {
      Objects.requireNonNull(idempotencyKey, "idempotencyKey");
      Objects.requireNonNull(effectiveDate, "effectiveDate");
    }
  }

  /** Commit success variant for a durably stored posting fact. */
  record Committed(
      PostingId postingId,
      IdempotencyKey idempotencyKey,
      LocalDate effectiveDate,
      Instant recordedAt)
      implements CommitEntryResult {

    /** Validates the committed success shape. */
    public Committed {
      Objects.requireNonNull(postingId, "postingId");
      Objects.requireNonNull(idempotencyKey, "idempotencyKey");
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(recordedAt, "recordedAt");
    }
  }

  /** Preflight rejection variant for a request that remains fully advisory. */
  record PreflightRejected(IdempotencyKey requestIdempotencyKey, PostingRejection rejection)
      implements PreflightEntryResult {
    /** Validates the preflight rejection payload returned to operating surfaces. */
    public PreflightRejected {
      Objects.requireNonNull(requestIdempotencyKey, "requestIdempotencyKey");
      Objects.requireNonNull(rejection, "rejection");
    }
  }

  /** Commit rejection variant for a request that did not cross the durable write boundary. */
  record CommitRejected(IdempotencyKey requestIdempotencyKey, PostingRejection rejection)
      implements CommitEntryResult {

    /** Validates the commit rejection payload returned to operating surfaces. */
    public CommitRejected {
      Objects.requireNonNull(requestIdempotencyKey, "requestIdempotencyKey");
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
