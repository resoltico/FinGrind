package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;

/** Internal bookkeeping command for validating or committing one journal entry. */
public record PostingCommand(
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel)
    implements PostingRequestModel {
  /** Validates one bookkeeping posting command. */
  public PostingCommand {
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
  }
}
