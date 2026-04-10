package dev.erst.fingrind.runtime;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Objects;
import java.util.Optional;

/** Committed posting fact carried across runtime persistence seams. */
public record PostingFact(
    PostingId postingId,
    JournalEntry journalEntry,
    Optional<ReversalReference> reversalReference,
    CommittedProvenance provenance) {
  /** Validates the canonical fact shape stored by runtime adapters. */
  public PostingFact {
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(journalEntry, "journalEntry");
    reversalReference = reversalReference == null ? Optional.empty() : reversalReference;
    Objects.requireNonNull(provenance, "provenance");
  }
}
