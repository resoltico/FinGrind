package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import java.util.Objects;

/** Closed family of ordinary commit outcomes returned by the posting seam. */
public sealed interface PostingCommitResult
    permits PostingCommitResult.Committed, PostingCommitResult.Rejected {

  /** Successful durable commit outcome carrying the stored posting fact. */
  record Committed(PostingFact postingFact) implements PostingCommitResult {
    /** Validates the committed posting result. */
    public Committed {
      Objects.requireNonNull(postingFact, "postingFact");
    }
  }

  /** Commit outcome carrying a deterministic application rejection. */
  record Rejected(PostingRejection rejection) implements PostingCommitResult {
    /** Validates the deterministic rejection payload. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
