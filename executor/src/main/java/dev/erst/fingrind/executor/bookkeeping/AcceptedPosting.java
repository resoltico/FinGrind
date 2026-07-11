package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Fully resolved posting shape accepted against one concrete book state snapshot. */
public record AcceptedPosting(
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    PostingKind postingKind,
    PostingOriginKind postingOriginKind,
    AccountingEvidence evidence,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel,
    @Nullable BookkeepingEntry callerAuthoredEntryOrNull,
    @Nullable BookkeepingEntry resolvedOriginatingEntryOrNull,
    List<InventoryMovementRecord> inventoryMovements,
    Map<AccountCode, InventoryAccountState> resultingInventoryStates)
    implements PostingRequestModel {
  /** Validates one accepted posting payload. */
  public AcceptedPosting {
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
    inventoryMovements =
        List.copyOf(Objects.requireNonNull(inventoryMovements, "inventoryMovements"));
    resultingInventoryStates =
        Map.copyOf(Objects.requireNonNull(resultingInventoryStates, "resultingInventoryStates"));
    PostingOriginatingEntryValidator.requireCallerAuthoredMatches(
        callerAuthoredEntryOrNull,
        postingKind,
        postingOriginKind,
        postingLineage,
        "accepted posting");
    PostingOriginatingEntryValidator.requireResolvedMatches(
        resolvedOriginatingEntryOrNull,
        postingKind,
        postingOriginKind,
        journalEntry,
        postingLineage,
        "accepted posting");
  }

  @Override
  public Optional<BookkeepingEntry> callerAuthoredEntry() {
    return Optional.ofNullable(callerAuthoredEntryOrNull);
  }

  @Override
  public Optional<BookkeepingEntry> resolvedOriginatingEntry() {
    return Optional.ofNullable(resolvedOriginatingEntryOrNull);
  }

  /**
   * Materializes one committed posting using the accepted resolved truth and durable provenance.
   */
  public CommittedPosting materialize(PostingId postingId, CommittedProvenance provenance) {
    return new CommittedPosting(
        Objects.requireNonNull(postingId, "postingId"),
        journalEntry,
        postingLineage,
        postingKind,
        postingOriginKind,
        evidence,
        Objects.requireNonNull(provenance, "provenance"),
        callerAuthoredEntryOrNull,
        resolvedOriginatingEntryOrNull);
  }
}
