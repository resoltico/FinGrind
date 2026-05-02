package dev.erst.fingrind.cli;

import java.util.Objects;

/** Shared invariant owner for CLI-request fuzz entrypoints that start from raw JSON bytes. */
final class CliRequestFuzzAssertions {
  private CliRequestFuzzAssertions() {}

  static void readPostEntryCommand(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    try {
      CliFuzzFixtures.readPostEntryCommand(input);
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }
}
