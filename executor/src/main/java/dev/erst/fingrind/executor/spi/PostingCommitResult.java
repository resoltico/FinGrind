package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;

/** Closed family of ordinary commit outcomes returned by the posting seam. */
public sealed interface PostingCommitResult
    permits PostingCommitResult.Committed, PostingCommitResult.Rejected {

  /** Successful durable commit outcome carrying the stored posting fact. */
  record Committed(CommittedPosting postingFact) implements PostingCommitResult {
    /** Validates the committed posting result. */
    public Committed {
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
