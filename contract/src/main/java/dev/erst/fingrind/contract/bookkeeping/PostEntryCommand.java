package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;

/** Application command for preflighting or committing one caller-authored bookkeeping entry. */
public record PostEntryCommand(
    BookkeepingEntry entry,
    AccountingEvidence evidence,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel) {
  /** Validates the application command before it reaches book-session adapters. */
  public PostEntryCommand {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(requestProvenance, "requestProvenance");
    Objects.requireNonNull(sourceChannel, "sourceChannel");
  }
}
