package dev.erst.fingrind.cli;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared invariant helpers for package-private CLI JSON model records. */
final class CliJsonModelValidation {
  private CliJsonModelValidation() {}

  static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null.");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return normalized;
  }

  static @Nullable String requireOptionalText(@Nullable String value, String fieldName) {
    if (value == null) {
      return null;
    }
    return requireText(value, fieldName);
  }

  static <T> T requireValue(T value, String fieldName) {
    return Objects.requireNonNull(value, fieldName + " must not be null.");
  }

  static <T> List<T> copyList(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  static void requirePositive(int value, String fieldName) {
    if (value < 1) {
      throw new IllegalArgumentException(fieldName + " must be positive.");
    }
  }

  static void requireNonNegative(int value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must not be negative.");
    }
  }
}
