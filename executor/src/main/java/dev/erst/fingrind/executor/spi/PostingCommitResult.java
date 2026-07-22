package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Closed family of ordinary commit outcomes returned by the posting seam. */
public sealed interface PostingCommitResult
    permits PostingCommitResult.Committed, PostingCommitResult.Rejected {

  /** Successful durable commit outcome carrying the stored posting fact. */
  record Committed(
      CommittedPosting postingFact,
      boolean idempotentReplay,
      @Nullable AttestationVerification attestationVerification)
      implements PostingCommitResult {
    /** Validates the committed posting result. */
    public Committed {
      Objects.requireNonNull(postingFact, "postingFact");
      if (idempotentReplay && attestationVerification != null) {
        throw new IllegalArgumentException(
            "An idempotent replay must not claim a newly appended attestation operation.");
      }
    }
  }

  /** Commit outcome carrying a deterministic application rejection. */
  record Rejected(BookkeepingPostingRejection rejection) implements PostingCommitResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
