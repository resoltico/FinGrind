package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;

/** Internal bookkeeping command for validating or committing one journal entry. */
public record PostingCommand(
    PostingKind postingKind,
    PostingOriginKind postingOriginKind,
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    AccountingEvidence evidence,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel)
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
  }
}
