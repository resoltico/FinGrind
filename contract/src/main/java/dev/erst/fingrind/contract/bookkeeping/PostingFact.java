package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Committed posting fact carried across application-owned book sessions. */
public record PostingFact(
    PostingId postingId,
    JournalEntry journalEntry,
    PostingLineage postingLineage,
    PostingKind postingKind,
    PostingOriginKind postingOriginKind,
    AccountingEvidence evidence,
    CommittedProvenance provenance,
    @Nullable BookkeepingEntry originatingEntry) {
  /** Validates the canonical fact shape stored by book-session adapters. */
  public PostingFact {
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(provenance, "provenance");
    requireMatchingOriginatingEntry(
        originatingEntry, postingKind, postingOriginKind, postingLineage);
  }

  /** Builds one committed posting fact without retained caller-authored entry details. */
  public PostingFact(
      PostingId postingId,
      JournalEntry journalEntry,
      PostingLineage postingLineage,
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      AccountingEvidence evidence,
      CommittedProvenance provenance) {
    this(
        postingId,
        journalEntry,
        postingLineage,
        postingKind,
        postingOriginKind,
        evidence,
        provenance,
        null);
  }

  /** Returns the optional reversal lineage descriptor for this committed fact. */
  public Optional<ReversalReference> reversalReference() {
    return postingLineage.reversalReference();
  }

  /** Returns the optional reversal reason carried by this committed fact. */
  public Optional<ReversalReason> reversalReason() {
    return postingLineage.reversalReason();
  }

  /** Returns the optional caller-authored entry facts retained with this committed fact. */
  public Optional<BookkeepingEntry> callerAuthoredEntry() {
    return Optional.ofNullable(originatingEntry);
  }

  private static void requireMatchingOriginatingEntry(
      @Nullable BookkeepingEntry originatingEntry,
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      PostingLineage postingLineage) {
    if (originatingEntry == null) {
      return;
    }
    if (originatingEntry.postingKind() != postingKind) {
      throw new IllegalArgumentException(
          "originatingEntry postingKind must match the committed posting fact.");
    }
    if (originatingEntry.postingOriginKind() != postingOriginKind) {
      throw new IllegalArgumentException(
          "originatingEntry postingOriginKind must match the committed posting fact.");
    }
    if (!originatingEntry.postingLineage().equals(postingLineage)) {
      throw new IllegalArgumentException(
          "originatingEntry postingLineage must match the committed posting fact lineage.");
    }
  }
}
