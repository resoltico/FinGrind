package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable public SQLite library-loading modes exposed through environment discovery. */
public enum SqliteLibraryMode implements WireValue {
  MANAGED_ONLY("managed-only");

  private final String wireValue;

  SqliteLibraryMode(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this SQLite library mode. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public SQLite library mode. */
  public static List<String> wireValues() {
    return WireValue.wireValues(SqliteLibraryMode.class);
  }

  /** Parses one stable public SQLite library mode. */
  public static SqliteLibraryMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        SqliteLibraryMode.class, wireValue, "Unsupported SQLite library mode");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
