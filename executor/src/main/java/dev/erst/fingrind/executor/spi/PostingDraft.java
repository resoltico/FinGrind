package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.PostingRequestModel;
import java.util.Objects;

/** Commit-ready posting draft that defers durable posting-id allocation until store acceptance. */
public record PostingDraft(
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    PostingKind postingKind,
    AccountingEvidence evidence,
    CommittedProvenance provenance)
    implements PostingRequestModel {
  /** Validates the durable commit draft before one book session materializes it. */
  public PostingDraft {
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(provenance, "provenance");
  }

  @Override
  public PostingLineageModel postingLineage() {
    return postingLineage;
  }

  @Override
  public RequestProvenance requestProvenance() {
    return provenance.requestProvenance();
  }

  /** Materializes one durable posting fact after the store accepts this draft for commit. */
  public CommittedPosting materialize(PostingId postingId) {
    return new CommittedPosting(
        postingId, journalEntry, postingLineage, postingKind, evidence, provenance);
  }
}
