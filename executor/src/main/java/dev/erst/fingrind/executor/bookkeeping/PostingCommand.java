package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Internal bookkeeping command for validating or committing one journal entry. */
public record PostingCommand(
    PostingKind postingKind,
    PostingOriginKind postingOriginKind,
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    AccountingEvidence evidence,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel,
    @Nullable BookkeepingEntry callerAuthoredEntryOrNull,
    @Nullable BookkeepingEntry resolvedOriginatingEntryOrNull)
    implements PostingRequestModel {
  /** Validates one bookkeeping posting command. */
  public PostingCommand {
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
    PostingOriginatingEntryValidator.requireCallerAuthoredMatches(
        callerAuthoredEntryOrNull,
        postingKind,
        postingOriginKind,
        postingLineage,
        "posting command");
    PostingOriginatingEntryValidator.requireResolvedMatches(
        resolvedOriginatingEntryOrNull,
        postingKind,
        postingOriginKind,
        journalEntry,
        postingLineage,
        "posting command");
  }

  /** Builds one posting command without retained caller-authored entry facts. */
  public PostingCommand(
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      AccountingEvidence evidence,
      RequestProvenance requestProvenance,
      SourceChannel sourceChannel) {
    this(
        postingKind,
        postingOriginKind,
        journalEntry,
        postingLineage,
        evidence,
        requestProvenance,
        sourceChannel,
        null,
        null);
  }

  @Override
  public Optional<BookkeepingEntry> callerAuthoredEntry() {
    return Optional.ofNullable(callerAuthoredEntryOrNull);
  }

  @Override
  public Optional<BookkeepingEntry> resolvedOriginatingEntry() {
    return Optional.ofNullable(resolvedOriginatingEntryOrNull);
  }
}
