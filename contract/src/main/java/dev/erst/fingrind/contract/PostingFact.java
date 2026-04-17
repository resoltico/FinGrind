package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Objects;
import java.util.Optional;

/** Committed posting fact carried across application-owned book sessions. */
public record PostingFact(
    PostingId postingId,
    JournalEntry journalEntry,
    Optional<ReversalReference> reversalReference,
    CommittedProvenance provenance) {
  /** Validates the canonical fact shape stored by book-session adapters. */
  public PostingFact {
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(journalEntry, "journalEntry");
    reversalReference = reversalReference == null ? Optional.empty() : reversalReference;
    Objects.requireNonNull(provenance, "provenance");
  }
}
