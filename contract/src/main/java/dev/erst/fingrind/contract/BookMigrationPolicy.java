package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Canonical migration policy vocabulary for on-disk FinGrind books. */
public enum BookMigrationPolicy implements WireValue {
  SEQUENTIAL_IN_PLACE;

  /** Returns the stable wire value exposed to callers. */
  @Override
  public String wireValue() {
    return switch (this) {
      case SEQUENTIAL_IN_PLACE -> "sequential-in-place";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookMigrationPolicy.class);
  }

  /** Parses one stable wire value. */
  public static BookMigrationPolicy fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        BookMigrationPolicy.class, wireValue, "Unsupported book migration policy");
  }

  @Override
  public String toString() {
    return wireValue();
  }
}
