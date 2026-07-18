package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.PostingId;
import java.util.Objects;

/** Refusal for a reversal whose selected target is itself already one reversal posting. */
public record ReversalTargetIsReversal(PostingId priorPostingId)
    implements WorkflowBookkeepingPostingRejection {
  public ReversalTargetIsReversal {
    Objects.requireNonNull(priorPostingId, "priorPostingId");
  }
}
