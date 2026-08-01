package dev.erst.fingrind.cli;

import java.util.Objects;

/** Shared invariant owner for ledger-plan fuzz entrypoints that start from raw JSON bytes. */
final class LedgerPlanRequestFuzzAssertions {
  private LedgerPlanRequestFuzzAssertions() {}

  static void readLedgerPlan(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    try {
      LedgerPlanFuzzAssertions.executeAndAssert(CliFuzzFixtures.readLedgerPlan(input));
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid plan/domain shapes are expected for many fuzz inputs.
    }
  }
}
