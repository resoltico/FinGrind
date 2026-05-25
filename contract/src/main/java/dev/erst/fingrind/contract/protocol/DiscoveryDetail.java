package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Selects how much discovery-surface detail the CLI returns. */
public enum DiscoveryDetail implements WireValue {
  MINIMAL("minimal"),
  COMPACT("compact"),
  FULL("full");

  private final String wireValue;

  DiscoveryDetail(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns all stable discovery-detail tokens in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(DiscoveryDetail.class);
  }

  /** Parses one stable discovery-detail token. */
  public static DiscoveryDetail fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        DiscoveryDetail.class, wireValue, "Unsupported discovery detail");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
