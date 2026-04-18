package dev.erst.fingrind.contract;

import java.util.Arrays;
import java.util.Objects;

/** Canonical migration policy vocabulary for on-disk FinGrind books. */
public enum BookMigrationPolicy {
  SEQUENTIAL_IN_PLACE("sequential-in-place");

  private final String wireValue;

  BookMigrationPolicy(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value exposed to callers. */
  public String wireValue() {
    return wireValue;
  }

  /** Parses one stable wire value. */
  public static BookMigrationPolicy fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(policy -> policy.wireValue.equals(wireValue))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unsupported book migration policy: " + wireValue));
  }
}
