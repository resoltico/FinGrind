package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReference;
import java.util.Optional;

/** Minimal posting shape shared by preflight commands and durable commit drafts. */
public interface PostingRequest {
  /** Returns the journal entry carried by this posting attempt. */
  JournalEntry journalEntry();

  /** Returns the optional reversal lineage descriptor for this posting attempt. */
  Optional<ReversalReference> reversalReference();

  /** Returns the caller-supplied request provenance for this posting attempt. */
  RequestProvenance requestProvenance();
}
