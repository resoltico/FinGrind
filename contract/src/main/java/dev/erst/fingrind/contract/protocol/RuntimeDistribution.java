package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable runtime-distribution identifiers exposed by the CLI environment contract. */
public enum RuntimeDistribution implements WireValue {
  DIRECT_JAVA_INVOCATION("direct-java-invocation"),
  SOURCE_CHECKOUT_GRADLE("source-checkout-gradle"),
  CONTAINER_IMAGE("container-image"),
  SELF_CONTAINED_BUNDLE("self-contained-bundle");

  private final String wireValue;

  RuntimeDistribution(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this runtime distribution. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable runtime-distribution identifier. */
  public static List<String> wireValues() {
    return WireValue.wireValues(RuntimeDistribution.class);
  }

  /** Parses one stable runtime-distribution identifier. */
  public static RuntimeDistribution fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        RuntimeDistribution.class, wireValue, "Unsupported runtime distribution");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
