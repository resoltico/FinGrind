package dev.erst.fingrind.runtime;

import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProvenanceEnvelope;
import java.util.Objects;

/** Committed posting fact carried across runtime persistence seams. */
public record PostingFact(
    PostingId postingId, JournalEntry journalEntry, ProvenanceEnvelope provenance) {
  /** Validates the canonical fact shape stored by runtime adapters. */
  public PostingFact {
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(provenance, "provenance");
  }
}
