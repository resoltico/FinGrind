package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Objects;
import java.util.Optional;

/** Internal bookkeeping lineage carried by posting commands, drafts, and committed facts. */
public sealed interface PostingLineageModel
    permits PostingLineageModel.Direct, PostingLineageModel.Reversal {
  /** Returns the optional reversal target. */
  Optional<ReversalReference> reversalReference();

  /** Returns the optional reversal reason. */
  Optional<ReversalReason> reversalReason();

  /** Returns whether this lineage targets one prior posting. */
  default boolean isReversal() {
    return reversalReference().isPresent();
  }

  /** Builds direct lineage for a normal posting. */
  static PostingLineageModel direct() {
    return new Direct();
  }

  /** Builds reversal lineage for a posting that negates one prior posting. */
  static PostingLineageModel reversal(ReversalReference reversalReference, ReversalReason reason) {
    return new Reversal(reversalReference, reason);
  }

  /** Direct lineage with no reversal target. */
  record Direct() implements PostingLineageModel {
    @Override
    public Optional<ReversalReference> reversalReference() {
      return Optional.empty();
    }

    @Override
    public Optional<ReversalReason> reversalReason() {
      return Optional.empty();
    }
  }

  /** Reversal lineage targeting one prior posting. */
  record Reversal(ReversalReference reference, ReversalReason reason)
      implements PostingLineageModel {
    /** Validates one reversal lineage. */
    public Reversal {
      Objects.requireNonNull(reference, "reference");
      Objects.requireNonNull(reason, "reason");
    }

    @Override
    public Optional<ReversalReference> reversalReference() {
      return Optional.of(reference);
    }

    @Override
    public Optional<ReversalReason> reversalReason() {
      return Optional.of(reason);
    }
  }
}
