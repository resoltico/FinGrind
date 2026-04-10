package dev.erst.fingrind.application;

import dev.erst.fingrind.core.CorrectionReference;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;
import java.util.Optional;

/** Application command for preflighting or committing one journal entry. */
public record PostEntryCommand(
    JournalEntry journalEntry,
    Optional<CorrectionReference> correctionReference,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel) {
  /** Validates the application command before it reaches runtime ports. */
  public PostEntryCommand {
    Objects.requireNonNull(journalEntry, "journalEntry");
    correctionReference = correctionReference == null ? Optional.empty() : correctionReference;
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
  }
}
