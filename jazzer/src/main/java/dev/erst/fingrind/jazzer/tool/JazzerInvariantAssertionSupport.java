package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;

/** Shared invariant assertion helper for deterministic Jazzer support code. */
final class JazzerInvariantAssertionSupport {
  private JazzerInvariantAssertionSupport() {}

  static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(Objects.requireNonNull(message, "message must not be null"));
    }
  }
}
