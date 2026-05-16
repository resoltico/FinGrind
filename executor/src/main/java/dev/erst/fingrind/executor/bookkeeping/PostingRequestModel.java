package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;

/** Minimal bookkeeping posting shape shared by preflight commands and commit drafts. */
public sealed interface PostingRequestModel
    permits PostingCommand, dev.erst.fingrind.executor.spi.PostingDraft {
  /** Returns the journal entry carried by this posting attempt. */
  JournalEntry journalEntry();

  /** Returns the lineage carried by this posting attempt. */
  PostingLineageModel postingLineage();

  /** Returns the canonical durable posting kind for this posting attempt. */
  PostingKind postingKind();

  /** Returns the optional reversal target. */
  default java.util.Optional<ReversalReference> reversalReference() {
    return postingLineage().reversalReference();
  }

  /** Returns the optional reversal reason. */
  default java.util.Optional<ReversalReason> reversalReason() {
    return postingLineage().reversalReason();
  }

  /** Returns the caller-supplied request provenance. */
  RequestProvenance requestProvenance();
}
