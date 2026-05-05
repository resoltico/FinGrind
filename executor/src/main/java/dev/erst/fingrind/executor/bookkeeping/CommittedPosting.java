package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Objects;
import java.util.Optional;

/** Internal bookkeeping fact for one committed posting. */
public record CommittedPosting(
    PostingId postingId,
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    CommittedProvenance provenance) {
  /** Validates one committed posting fact. */
  public CommittedPosting {
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(provenance, "provenance");
  }

  /** Returns the optional reversal target. */
  public Optional<ReversalReference> reversalReference() {
    return postingLineage.reversalReference();
  }

  /** Returns the optional reversal reason. */
  public Optional<ReversalReason> reversalReason() {
    return postingLineage.reversalReason();
  }
}
