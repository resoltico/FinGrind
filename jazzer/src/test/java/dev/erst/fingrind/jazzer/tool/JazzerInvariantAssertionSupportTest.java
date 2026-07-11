package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Covers the shared invariant assertion helper used by deterministic Jazzer support code. */
class JazzerInvariantAssertionSupportTest {
  @Test
  void require_accepts_true_conditions() {
    assertDoesNotThrow(() -> JazzerInvariantAssertionSupport.require(true, "ok"));
  }

  @Test
  void require_throws_assertion_error_for_false_conditions() {
    AssertionError error =
        assertThrows(
            AssertionError.class,
            () -> JazzerInvariantAssertionSupport.require(false, "broken invariant"));

    assertEquals("broken invariant", error.getMessage());
  }
}
