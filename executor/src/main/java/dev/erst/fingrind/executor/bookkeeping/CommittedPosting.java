package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
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

/** Internal bookkeeping fact for one committed posting. */
public record CommittedPosting(
    PostingId postingId,
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    PostingKind postingKind,
    PostingOriginKind postingOriginKind,
    AccountingEvidence evidence,
    CommittedProvenance provenance,
    @Nullable BookkeepingEntry callerAuthoredEntryOrNull,
    @Nullable BookkeepingEntry resolvedOriginatingEntryOrNull) {
  /** Validates one committed posting fact. */
  public CommittedPosting {
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(provenance, "provenance");
    PostingOriginatingEntryValidator.requireCallerAuthoredMatches(
        callerAuthoredEntryOrNull,
        postingKind,
        postingOriginKind,
        postingLineage,
        "committed posting");
    PostingOriginatingEntryValidator.requireResolvedMatches(
        resolvedOriginatingEntryOrNull,
        postingKind,
        postingOriginKind,
        journalEntry,
        postingLineage,
        "committed posting");
  }

  /** Builds one committed posting without retained caller-authored entry facts. */
  public CommittedPosting(
      PostingId postingId,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
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
        null,
        null);
  }

  /** Returns the optional reversal target. */
  public Optional<ReversalReference> reversalReference() {
    return postingLineage.reversalReference();
  }

  /** Returns the optional reversal reason. */
  public Optional<ReversalReason> reversalReason() {
    return postingLineage.reversalReason();
  }

  /** Returns the optional caller-authored entry facts retained with this committed posting. */
  public Optional<BookkeepingEntry> callerAuthoredEntry() {
    return Optional.ofNullable(callerAuthoredEntryOrNull);
  }

  /** Returns the optional executor-resolved entry facts retained with this committed posting. */
  public Optional<BookkeepingEntry> resolvedOriginatingEntry() {
    return Optional.ofNullable(resolvedOriginatingEntryOrNull);
  }
}
