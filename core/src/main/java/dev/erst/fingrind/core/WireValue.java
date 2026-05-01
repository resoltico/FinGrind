package dev.erst.fingrind.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stable public wire-value contract for machine-visible FinGrind vocabulary types. */
@FunctionalInterface
public interface WireValue {
  ClassValue<EnumVocabulary> ENUM_VOCABULARIES =
      new ClassValue<>() {
        @Override
        protected EnumVocabulary computeValue(Class<?> type) {
          if (!type.isEnum() || !WireValue.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("WireValue enum metadata requires an enum type.");
          }
          // ClassValue serializes construction for each enum type, so this ordered builder is not
          // a concurrent mutation surface and must preserve declaration order for the public
          // wire-value contract.
          Map<String, Enum<?>> valuesByWireValue = // NOPMD - see comment above.
              new LinkedHashMap<>();
          for (Object value : type.getEnumConstants()) {
            Enum<?> constant = (Enum<?>) value;
            String wireValue = Objects.requireNonNull(((WireValue) value).wireValue(), "wireValue");
            if (wireValue.isBlank()) {
              throw new IllegalStateException(
                  type.getSimpleName() + " must not publish blank wire values.");
            }
            Enum<?> prior = valuesByWireValue.put(wireValue, constant);
            if (prior != null) {
              throw new IllegalStateException(
                  type.getSimpleName() + " duplicates wire value '" + wireValue + "'.");
            }
          }
          return new EnumVocabulary(
              List.copyOf(new ArrayList<>(valuesByWireValue.keySet())),
              Map.copyOf(valuesByWireValue));
        }
      };

  /** Returns the canonical externally visible token for this value. */
  String wireValue();

  /** Returns every stable wire value for one {@link WireValue} enum in declaration order. */
  static <E extends Enum<E> & WireValue> List<String> wireValues(Class<E> enumType) {
    return vocabulary(enumType).wireValues();
  }

  /** Parses one stable wire value for one {@link WireValue} enum. */
  static <E extends Enum<E> & WireValue> E fromWireValue(
      Class<E> enumType, String wireValue, String unsupportedValueLabel) {
    Objects.requireNonNull(wireValue, "wireValue");
    Objects.requireNonNull(unsupportedValueLabel, "unsupportedValueLabel");
    return vocabulary(enumType).requireValue(enumType, wireValue, unsupportedValueLabel);
  }

  private static <E extends Enum<E> & WireValue> EnumVocabulary vocabulary(Class<E> enumType) {
    Objects.requireNonNull(enumType, "enumType");
    return ENUM_VOCABULARIES.get(enumType);
  }

  /** Cached per-enum wire vocabulary. */
  final class EnumVocabulary {
    private final List<String> wireValues;
    private final Map<String, Enum<?>> valuesByWireValue;

    private EnumVocabulary(List<String> wireValues, Map<String, Enum<?>> valuesByWireValue) {
      this.wireValues = Objects.requireNonNull(wireValues, "wireValues");
      this.valuesByWireValue = Objects.requireNonNull(valuesByWireValue, "valuesByWireValue");
    }

    private List<String> wireValues() {
      return wireValues;
    }

    private <E extends Enum<E> & WireValue> E requireValue(
        Class<E> enumType, String wireValue, String unsupportedValueLabel) {
      Enum<?> value = valuesByWireValue.get(wireValue);
      if (value == null) {
        throw new IllegalArgumentException(unsupportedValueLabel + ": " + wireValue);
      }
      return enumType.cast(value);
    }
  }
}
