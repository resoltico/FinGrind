package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Closed result family for the entry write boundary. */
public sealed interface PostEntryResult
    permits PostEntryResult.PreflightAccepted, PostEntryResult.Committed, PostEntryResult.Rejected {

  /** Preflight-only success variant for a validated but not yet committed request. */
  record PreflightAccepted(IdempotencyKey idempotencyKey, LocalDate effectiveDate)
      implements PostEntryResult {

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
      implements PostEntryResult {

    /** Validates the committed success shape. */
    public Committed {
      Objects.requireNonNull(postingId, "postingId");
      Objects.requireNonNull(idempotencyKey, "idempotencyKey");
      Objects.requireNonNull(effectiveDate, "effectiveDate");
      Objects.requireNonNull(recordedAt, "recordedAt");
    }
  }

  /** Rejection variant for a request that does not cross the durable write boundary. */
  record Rejected(IdempotencyKey idempotencyKey, PostingRejection rejection)
      implements PostEntryResult {

    /** Validates the rejection payload returned to operating surfaces. */
    public Rejected {
      Objects.requireNonNull(idempotencyKey, "idempotencyKey");
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
