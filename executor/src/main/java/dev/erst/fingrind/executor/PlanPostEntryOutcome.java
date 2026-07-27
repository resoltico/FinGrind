package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;

/** Internal post-entry result for a child of one aggregate attested ledger plan. */
public sealed interface PlanPostEntryOutcome
    permits PlanPostEntryOutcome.Committed, PlanPostEntryOutcome.Rejected {
  /** Durable child posting, either newly collected for the plan or an idempotent replay. */
  record Committed(CommittedPosting postingFact, boolean idempotentReplay)
      implements PlanPostEntryOutcome {
    public Committed {
      Objects.requireNonNull(postingFact, "postingFact");
    }
  }

  /** Deterministic rejection before a child posting is committed. */
  record Rejected(PostingRejection rejection) implements PlanPostEntryOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
