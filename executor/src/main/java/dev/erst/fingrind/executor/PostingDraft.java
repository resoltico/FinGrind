package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRequest;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Objects;
import java.util.Optional;

/** Commit-ready posting draft that defers durable posting-id allocation until store acceptance. */
public record PostingDraft(
    JournalEntry journalEntry,
    Optional<ReversalReference> reversalReference,
    CommittedProvenance provenance)
    implements PostingRequest {
  /** Validates the durable commit draft before one book session materializes it. */
  public PostingDraft {
    Objects.requireNonNull(journalEntry, "journalEntry");
    reversalReference = reversalReference == null ? Optional.empty() : reversalReference;
    Objects.requireNonNull(provenance, "provenance");
  }

  @Override
  public RequestProvenance requestProvenance() {
    return provenance.requestProvenance();
  }

  /** Materializes one durable posting fact after the store accepts this draft for commit. */
  public PostingFact materialize(PostingId postingId) {
    return new PostingFact(postingId, journalEntry, reversalReference, provenance);
  }
}
