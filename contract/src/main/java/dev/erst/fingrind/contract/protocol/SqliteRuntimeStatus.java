package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable machine-readable SQLite runtime availability states. */
public enum SqliteRuntimeStatus implements WireValue {
  READY("ready"),
  UNAVAILABLE("unavailable"),
  FAILED("failed"),
  INCOMPATIBLE("incompatible");

  private final String wireValue;

  SqliteRuntimeStatus(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this SQLite runtime status. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable SQLite runtime status. */
  public static List<String> wireValues() {
    return WireValue.wireValues(SqliteRuntimeStatus.class);
  }

  /** Parses one stable SQLite runtime status. */
  public static SqliteRuntimeStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        SqliteRuntimeStatus.class, wireValue, "Unsupported SQLite runtime status");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
