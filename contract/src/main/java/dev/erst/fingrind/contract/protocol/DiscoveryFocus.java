package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Selects which discovery concern one JSON discovery response should return. */
public enum DiscoveryFocus implements WireValue {
  OVERVIEW("overview"),
  COMMANDS("commands"),
  STORAGE("storage"),
  REQUEST_INPUT("request-input"),
  CURRENCY_MODEL("currency-model"),
  BOOKKEEPING_KERNEL("bookkeeping-kernel"),
  CAPABILITY_CATALOG("capability-catalog"),
  RESPONSE_CONTRACT("response-contract");

  private final String wireValue;

  DiscoveryFocus(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns all stable discovery-focus tokens in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(DiscoveryFocus.class);
  }

  /** Parses one stable discovery-focus token. */
  public static DiscoveryFocus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(DiscoveryFocus.class, wireValue, "Unsupported discovery focus");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
