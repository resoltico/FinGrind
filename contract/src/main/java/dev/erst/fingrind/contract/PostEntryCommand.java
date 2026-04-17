package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;
import java.util.Optional;

/** Application command for preflighting or committing one journal entry. */
public record PostEntryCommand(
    JournalEntry journalEntry,
    Optional<ReversalReference> reversalReference,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel)
    implements PostingRequest {
  /** Validates the application command before it reaches book-session adapters. */
  public PostEntryCommand {
    Objects.requireNonNull(journalEntry, "journalEntry");
    reversalReference = reversalReference == null ? Optional.empty() : reversalReference;
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
  }
}
