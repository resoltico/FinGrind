package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;

/** Application command for preflighting or committing one journal entry. */
public record PostEntryCommand(
    PostingKind postingKind,
    JournalEntry journalEntry,
    PostingLineage postingLineage,
    AccountingEvidence evidence,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel)
    implements PostingRequest {
  /** Validates the application command before it reaches book-session adapters. */
  public PostEntryCommand {
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
  }
}
