package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.PostEntryCommandTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared parsing and deterministic-value fixtures for CLI-backed Jazzer harnesses. */
public final class CliFuzzFixtures {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);

  private CliFuzzFixtures() {}

  /** Parses one CLI request payload from bytes using the same reader used by the production CLI. */
  public static PostEntryCommand readPostEntryCommand(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    return new CliRequestReader(new ByteArrayInputStream(input)).readPostEntryCommand(Path.of("-"));
  }

  /** Derives the local bookkeeping posting command for one published post-entry command. */
  public static dev.erst.fingrind.executor.bookkeeping.PostingCommand bookkeepingCommand(
      PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return PostEntryCommandTranslator.toPostingCommand(command);
  }

  /** Returns the derived journal entry carried by one published command. */
  public static dev.erst.fingrind.core.JournalEntry journalEntry(PostEntryCommand command) {
    return bookkeepingCommand(command).journalEntry();
  }

  /** Returns the derived posting kind carried by one published command. */
  public static PostingKind postingKind(PostEntryCommand command) {
    return bookkeepingCommand(command).postingKind();
  }

  /** Returns the derived reversal target carried by one published command. */
  public static Optional<ReversalReference> reversalReference(PostEntryCommand command) {
    return bookkeepingCommand(command).postingLineage().reversalReference();
  }

  /** Returns the derived posting lineage carried by one published command. */
  public static dev.erst.fingrind.contract.bookkeeping.PostingLineage postingLineage(
      PostEntryCommand command) {
    return BookkeepingPublishedLanguageTranslator.toPublished(
        bookkeepingCommand(command).postingLineage());
  }

  /** Parses one ledger-plan payload from bytes using the production CLI request reader. */
  public static LedgerPlan readLedgerPlan(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    return new CliRequestReader(new ByteArrayInputStream(input)).readLedgerPlan(Path.of("-"));
  }

  /** Returns a deterministic posting-id generator for one fuzz iteration. */
  public static PostingIdGenerator postingIdGenerator(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    String postingId = UUID.nameUUIDFromBytes(input).toString();
    return () -> new PostingId(postingId);
  }

  /** Returns the deterministic clock shared by Jazzer harnesses and regression replay. */
  public static Clock fixedClock() {
    return FIXED_CLOCK;
  }
}
