package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Stable machine-readable migration policy modes for the protected-book format line. */
public enum BookMigrationPolicyMode implements WireValue {
  HARD_BREAK_REJECT_OLDER_FORMATS;

  @Override
  public String wireValue() {
    return switch (this) {
      case HARD_BREAK_REJECT_OLDER_FORMATS -> "hard-break-reject-older-formats";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookMigrationPolicyMode.class);
  }

  /** Parses one stable public wire value. */
  public static BookMigrationPolicyMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        BookMigrationPolicyMode.class, wireValue, "Unsupported book migration policy mode");
  }
}
