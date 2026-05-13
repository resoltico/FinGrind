package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import java.io.IOException;
import java.util.Objects;

/** Shared invariant owner for SQLite-backed single-book fuzz entrypoints. */
final class SqliteBookRoundTripFuzzAssertions {
  private SqliteBookRoundTripFuzzAssertions() {}

  static void roundTripSingleBook(byte[] input) throws IOException {
    Objects.requireNonNull(input, "input must not be null");
    try {
      roundTripParsedCommand(CliFuzzFixtures.readPostEntryCommand(input), input);
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }

  static void roundTripParsedCommand(PostEntryCommand command, byte[] input) throws IOException {
    SqliteRoundTripWorkflowAssertions.exerciseRoundTripWorkflow(command, input);
  }
}
