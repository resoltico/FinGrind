package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.PostingOriginatingEntryValidator;
import dev.erst.fingrind.executor.bookkeeping.PostingRequestModel;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Commit-ready posting draft that defers durable posting-id allocation until store acceptance. */
public record PostingDraft(
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    PostingKind postingKind,
    PostingOriginKind postingOriginKind,
    AccountingEvidence evidence,
    RequestFingerprint requestFingerprint,
    CommittedProvenance provenance,
    @Nullable BookkeepingEntry callerAuthoredEntryOrNull,
    @Nullable BookkeepingEntry resolvedOriginatingEntryOrNull)
    implements PostingRequestModel {
  /** Validates the durable commit draft before one book session materializes it. */
  public PostingDraft {
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(requestFingerprint, "requestFingerprint");
    Objects.requireNonNull(provenance, "provenance");
    PostingOriginatingEntryValidator.requireCallerAuthoredMatches(
        callerAuthoredEntryOrNull, postingKind, postingOriginKind, postingLineage, "posting draft");
    PostingOriginatingEntryValidator.requireResolvedMatches(
        resolvedOriginatingEntryOrNull,
        postingKind,
        postingOriginKind,
        journalEntry,
        postingLineage,
        "posting draft");
  }

  /** Builds one posting draft without retained caller-authored entry facts. */
  public PostingDraft(
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      AccountingEvidence evidence,
      RequestFingerprint requestFingerprint,
      CommittedProvenance provenance) {
    this(
        journalEntry,
        postingLineage,
        postingKind,
        postingOriginKind,
        evidence,
        requestFingerprint,
        provenance,
        null,
        null);
  }

  @Override
  public PostingLineageModel postingLineage() {
    return postingLineage;
  }

  @Override
  public RequestProvenance requestProvenance() {
    return provenance.requestProvenance();
  }

  @Override
  public SourceChannel sourceChannel() {
    return provenance.sourceChannel();
  }

  @Override
  public Optional<BookkeepingEntry> callerAuthoredEntry() {
    return Optional.ofNullable(callerAuthoredEntryOrNull);
  }

  @Override
  public Optional<BookkeepingEntry> resolvedOriginatingEntry() {
    return Optional.ofNullable(resolvedOriginatingEntryOrNull);
  }

  /** Materializes one durable posting fact after the store accepts this draft for commit. */
  public CommittedPosting materialize(PostingId postingId) {
    return new CommittedPosting(
        postingId,
        journalEntry,
        postingLineage,
        postingKind,
        postingOriginKind,
        evidence,
        provenance,
        callerAuthoredEntryOrNull,
        resolvedOriginatingEntryOrNull);
  }
}
