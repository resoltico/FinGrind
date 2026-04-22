package dev.erst.fingrind.contract.internal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared validation rules for exported machine-contract descriptor records. */
public final class ContractDescriptorValidation {
  private ContractDescriptorValidation() {}

  /** Requires one semantic text field to be non-null and non-blank. */
  public static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return normalized;
  }

  /** Requires one descriptor-owned reference field to be non-null. */
  public static <T> T requireValue(T value, String fieldName) {
    return Objects.requireNonNull(value, fieldName);
  }

  /** Requires one optional semantic text field to be blank-free when present. */
  public static @Nullable String requireOptionalText(@Nullable String value, String fieldName) {
    if (value == null) {
      return null;
    }
    return requireText(value, fieldName);
  }

  /** Copies one descriptor-owned list defensively. */
  public static <T> List<T> copyList(@Nullable List<T> values, String fieldName) {
    Objects.requireNonNull(fieldName, "fieldName");
    return values == null ? List.of() : List.copyOf(values);
  }
}
