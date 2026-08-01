package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Covers the public authenticated-head invariant on registry mutation outcomes. */
class ProtectedBookRegistryMutationOutcomeTest {
  @Test
  void mutated_rejectsANoncanonicalOperationHead() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookRegistryMutationOutcome.Mutated(
                Path.of("book.sqlite"), "enroll-key", BigInteger.ONE, "invalid-head"));
  }
}
