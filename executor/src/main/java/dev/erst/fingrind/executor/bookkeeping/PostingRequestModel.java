package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;

/** Minimal bookkeeping posting shape shared by preflight commands and commit drafts. */
public sealed interface PostingRequestModel
    permits PostingCommand, dev.erst.fingrind.executor.spi.PostingDraft {
  /** Returns the journal entry carried by this posting attempt. */
  JournalEntry journalEntry();

  /** Returns the lineage carried by this posting attempt. */
  PostingLineageModel postingLineage();

  /** Returns the canonical durable posting kind for this posting attempt. */
  PostingKind postingKind();

  /** Returns the durable origin kind preserved for this posting attempt. */
  PostingOriginKind postingOriginKind();

  /** Returns the optional reversal target. */
  default java.util.Optional<ReversalReference> reversalReference() {
    return postingLineage().reversalReference();
  }

  /** Returns the optional reversal reason. */
  default java.util.Optional<ReversalReason> reversalReason() {
    return postingLineage().reversalReason();
  }

  /** Returns the first-class evidence bundle carried by this posting attempt. */
  AccountingEvidence evidence();

  /** Returns the caller-supplied request provenance. */
  RequestProvenance requestProvenance();

  /** Returns the logical source channel that owns this posting attempt. */
  SourceChannel sourceChannel();

  /** Returns the optional caller-authored entry facts retained for durable traceability. */
  default java.util.Optional<BookkeepingEntry> callerAuthoredEntry() {
    return java.util.Optional.empty();
  }
}
