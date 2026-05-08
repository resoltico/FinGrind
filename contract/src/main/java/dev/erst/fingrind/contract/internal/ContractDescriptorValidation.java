package dev.erst.fingrind.contract.internal;

import java.util.List;
import java.util.Map;
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
  public static <T> List<T> copyList(List<T> values, String fieldName) {
    Objects.requireNonNull(fieldName, "fieldName");
    Objects.requireNonNull(values, fieldName + " must not be null.");
    for (int index = 0; index < values.size(); index++) {
      if (values.get(index) == null) {
        throw new IllegalArgumentException(fieldName + "[" + index + "] must not be null.");
      }
    }
    return List.copyOf(values);
  }

  /** Copies one descriptor-owned map defensively while preserving insertion order. */
  public static <K, V> Map<K, V> copyMap(Map<K, V> values, String fieldName) {
    Objects.requireNonNull(fieldName, "fieldName");
    Objects.requireNonNull(values, fieldName + " must not be null.");
    var ordered = new java.util.LinkedHashMap<K, V>();
    for (Map.Entry<K, V> entry : values.entrySet()) {
      K key = Objects.requireNonNull(entry.getKey(), fieldName + " must not contain a null key.");
      V value =
          Objects.requireNonNull(
              entry.getValue(), fieldName + "[" + key + "] must not contain a null value.");
      ordered.put(key, value);
    }
    return java.util.Collections.unmodifiableMap(ordered);
  }
}
