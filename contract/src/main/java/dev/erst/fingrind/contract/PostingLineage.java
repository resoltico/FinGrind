package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Objects;
import java.util.Optional;

/** Structurally typed posting lineage carried by commands, drafts, and committed facts. */
public sealed interface PostingLineage permits PostingLineage.Direct, PostingLineage.Reversal {
  /** Returns the optional prior posting that this lineage reverses. */
  Optional<ReversalReference> reversalReference();

  /** Returns the optional human-readable reason carried by reversal postings. */
  Optional<ReversalReason> reversalReason();

  /** Returns whether this lineage records a reversal of one prior posting. */
  default boolean isReversal() {
    return reversalReference().isPresent();
  }

  /** Builds a direct posting lineage with no reversal target. */
  static PostingLineage direct() {
    return new Direct();
  }

  /** Builds one reversal lineage for the supplied prior posting and reason. */
  static PostingLineage reversal(ReversalReference reversalReference, ReversalReason reason) {
    return new Reversal(reversalReference, reason);
  }

  /** Direct posting with no reversal target. */
  record Direct() implements PostingLineage {
    @Override
    public Optional<ReversalReference> reversalReference() {
      return Optional.empty();
    }

    @Override
    public Optional<ReversalReason> reversalReason() {
      return Optional.empty();
    }
  }

  /** Reversal posting that targets one previously committed posting. */
  record Reversal(ReversalReference reference, ReversalReason reason) implements PostingLineage {
    /** Validates the reversal lineage. */
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
