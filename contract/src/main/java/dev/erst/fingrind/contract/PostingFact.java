package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Objects;
import java.util.Optional;

/** Committed posting fact carried across application-owned book sessions. */
public record PostingFact(
    PostingId postingId,
    JournalEntry journalEntry,
    PostingLineage postingLineage,
    CommittedProvenance provenance) {
  /** Validates the canonical fact shape stored by book-session adapters. */
  public PostingFact {
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(provenance, "provenance");
  }

  /** Returns the optional reversal lineage descriptor for this committed fact. */
  public Optional<ReversalReference> reversalReference() {
    return postingLineage.reversalReference();
  }

  /** Returns the optional reversal reason carried by this committed fact. */
  public Optional<ReversalReason> reversalReason() {
    return postingLineage.reversalReason();
  }
}
