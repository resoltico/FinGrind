package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable public storage-engine identifiers exposed by FinGrind environments. */
public enum StorageEngine implements WireValue {
  SQLITE("sqlite");

  private final String wireValue;

  StorageEngine(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this storage engine. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public storage-engine identifier. */
  public static List<String> wireValues() {
    return WireValue.wireValues(StorageEngine.class);
  }

  /** Parses one stable public storage-engine identifier. */
  public static StorageEngine fromWireValue(String wireValue) {
    return WireValue.fromWireValue(StorageEngine.class, wireValue, "Unsupported storage engine");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
