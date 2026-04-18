package dev.erst.fingrind.contract;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Canonical migration policy vocabulary for on-disk FinGrind books. */
public enum BookMigrationPolicy {
  SEQUENTIAL_IN_PLACE;

  /** Returns the stable wire value exposed to callers. */
  public String wireValue() {
    return switch (this) {
      case SEQUENTIAL_IN_PLACE -> "sequential-in-place";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return Arrays.stream(values()).map(BookMigrationPolicy::wireValue).toList();
  }

  /** Parses one stable wire value. */
  public static BookMigrationPolicy fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(value -> value.wireValue().equals(wireValue))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unsupported book migration policy: " + wireValue));
  }

  @Override
  public String toString() {
    return wireValue();
  }
}
