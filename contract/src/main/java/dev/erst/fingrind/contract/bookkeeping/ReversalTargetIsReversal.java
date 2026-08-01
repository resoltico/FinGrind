package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.PostingId;
import java.util.Objects;

/** Rejection for a reversal attempt whose selected target is already one reversal posting. */
public record ReversalTargetIsReversal(PostingId priorPostingId)
    implements WorkflowPostingRejection {
  /** Validates the reversal-target descriptor. */
  public ReversalTargetIsReversal {
    Objects.requireNonNull(priorPostingId, "priorPostingId");
  }
}
