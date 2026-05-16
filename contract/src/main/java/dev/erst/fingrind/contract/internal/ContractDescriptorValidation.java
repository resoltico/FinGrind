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

  /**
   * Requires one optional descriptor-owned reference field to be returned unchanged when present.
   */
  public static <T> @Nullable T requireOptionalValue(@Nullable T value, String fieldName) {
    if (value == null) {
      return null;
    }
    return requireValue(value, fieldName);
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

  /** Copies one JSON-schema map recursively, accepting only scalar, map, and list schema values. */
  public static Map<String, Object> copySchemaMap(Map<String, Object> values, String fieldName) {
    Objects.requireNonNull(fieldName, "fieldName");
    Objects.requireNonNull(values, fieldName + " must not be null.");
    var ordered = new java.util.LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String key =
          Objects.requireNonNull(entry.getKey(), fieldName + " must not contain a null key.");
      Object value =
          Objects.requireNonNull(
              entry.getValue(), fieldName + "[" + key + "] must not contain a null value.");
      ordered.put(key, deepFrozenSchemaValue(value, fieldName + "[" + key + "]"));
    }
    return java.util.Collections.unmodifiableMap(ordered);
  }

  private static Object deepFrozenSchemaValue(Object value, String fieldName) {
    if (value instanceof String
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Enum<?>) {
      return value;
    }
    if (value instanceof Map<?, ?> mapValue) {
      var normalized = new java.util.LinkedHashMap<String, Object>();
      for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
        Object key =
            Objects.requireNonNull(
                entry.getKey(), fieldName + " must not contain a nested null key.");
        if (!(key instanceof String keyText)) {
          throw new IllegalArgumentException(
              fieldName + " must contain only string-keyed schema maps.");
        }
        Object nestedValue =
            Objects.requireNonNull(
                entry.getValue(), fieldName + "[" + keyText + "] must not contain a null value.");
        normalized.put(
            keyText, deepFrozenSchemaValue(nestedValue, fieldName + "[" + keyText + "]"));
      }
      return java.util.Collections.unmodifiableMap(normalized);
    }
    if (value instanceof List<?> listValue) {
      var normalized = new java.util.ArrayList<Object>(listValue.size());
      for (int index = 0; index < listValue.size(); index++) {
        Object element =
            Objects.requireNonNull(
                listValue.get(index), fieldName + "[" + index + "] must not contain a null value.");
        normalized.add(deepFrozenSchemaValue(element, fieldName + "[" + index + "]"));
      }
      return java.util.Collections.unmodifiableList(normalized);
    }
    throw new IllegalArgumentException(
        fieldName + " contains unsupported schema value type: " + value.getClass().getName() + ".");
  }
}
