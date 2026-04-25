package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable public distribution identifiers for downloadable FinGrind CLI artifacts. */
public enum PublicCliDistribution implements WireValue {
  SELF_CONTAINED_BUNDLE("self-contained-bundle");

  private final String wireValue;

  PublicCliDistribution(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this public CLI distribution. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public CLI distribution identifier. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PublicCliDistribution.class);
  }

  /** Parses one stable public CLI distribution identifier. */
  public static PublicCliDistribution fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        PublicCliDistribution.class, wireValue, "Unsupported public CLI distribution");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
