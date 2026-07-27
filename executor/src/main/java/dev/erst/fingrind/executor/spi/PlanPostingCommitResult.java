package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;

/** Closed result family for a posting attempted only as an aggregate ledger-plan child. */
public sealed interface PlanPostingCommitResult
    permits PlanPostingCommitResult.Deferred,
        PlanPostingCommitResult.Replayed,
        PlanPostingCommitResult.Rejected {
  /** Newly persisted posting whose attestation is deferred to the enclosing plan. */
  record Deferred(CommittedPosting postingFact) implements PlanPostingCommitResult {
    public Deferred {
      Objects.requireNonNull(postingFact, "postingFact");
    }
  }

  /** Idempotent replay that added neither a posting nor an attestation child. */
  record Replayed(CommittedPosting postingFact) implements PlanPostingCommitResult {
    public Replayed {
      Objects.requireNonNull(postingFact, "postingFact");
    }
  }

  /** Deterministic refusal before a posting child mutation is persisted. */
  record Rejected(BookkeepingPostingRejection rejection) implements PlanPostingCommitResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
