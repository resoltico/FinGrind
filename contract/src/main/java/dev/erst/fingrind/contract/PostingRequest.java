package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;

/** Minimal posting shape shared by preflight commands and durable commit drafts. */
public interface PostingRequest {
  /** Returns the journal entry carried by this posting attempt. */
  JournalEntry journalEntry();

  /** Returns the structurally typed lineage descriptor for this posting attempt. */
  PostingLineage postingLineage();

  /** Returns the optional reversal lineage descriptor for this posting attempt. */
  default java.util.Optional<ReversalReference> reversalReference() {
    return postingLineage().reversalReference();
  }

  /** Returns the optional reversal reason carried by this posting attempt. */
  default java.util.Optional<ReversalReason> reversalReason() {
    return postingLineage().reversalReason();
  }

  /** Returns the caller-supplied request provenance for this posting attempt. */
  RequestProvenance requestProvenance();
}
