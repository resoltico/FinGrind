package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;

/** Closed family of ordinary commit outcomes returned by the posting seam. */
public sealed interface PostingCommitResult
    permits PostingCommitResult.Appended,
        PostingCommitResult.Replayed,
        PostingCommitResult.Rejected {

  /** Successful durable commit that appended one individual attestation operation. */
  record Appended(CommittedPosting postingFact, AttestationAppendOutcome.Appended attestationAppend)
      implements PostingCommitResult {
    /** Validates one immediately attested posting result. */
    public Appended {
      Objects.requireNonNull(postingFact, "postingFact");
      Objects.requireNonNull(attestationAppend, "attestationAppend");
    }
  }

  /** Successful idempotent replay that appended no attestation operation. */
  record Replayed(CommittedPosting postingFact) implements PostingCommitResult {
    public Replayed {
      Objects.requireNonNull(postingFact, "postingFact");
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
