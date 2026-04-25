package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable public storage-driver identifiers exposed by FinGrind environments. */
public enum StorageDriver implements WireValue {
  SQLITE_FFM_SQLITE3MC("sqlite-ffm-sqlite3mc");

  private final String wireValue;

  StorageDriver(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this storage driver. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public storage-driver identifier. */
  public static List<String> wireValues() {
    return WireValue.wireValues(StorageDriver.class);
  }

  /** Parses one stable public storage-driver identifier. */
  public static StorageDriver fromWireValue(String wireValue) {
    return WireValue.fromWireValue(StorageDriver.class, wireValue, "Unsupported storage driver");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
