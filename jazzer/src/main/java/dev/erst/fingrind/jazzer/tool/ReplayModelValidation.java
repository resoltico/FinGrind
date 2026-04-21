package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;

/** Shared invariant helpers for Jazzer replay model records. */
final class ReplayModelValidation {
  private ReplayModelValidation() {}

  static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return normalized;
  }

  static <T extends ReplayDetails> T requireDetails(T value, String fieldName) {
    return Objects.requireNonNull(value, fieldName + " must not be null");
  }
}
