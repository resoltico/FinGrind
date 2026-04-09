package dev.erst.fingrind.application;

import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.ProvenanceEnvelope;
import java.util.Objects;

/** Application command for preflighting or committing one journal entry. */
public record PostEntryCommand(JournalEntry journalEntry, ProvenanceEnvelope provenance) {
  /** Validates the application command before it reaches runtime ports. */
  public PostEntryCommand {
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(provenance, "provenance");
  }
}
