package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable request-field presence metadata published through the machine-readable contract. */
public enum RequestFieldPresence implements WireValue {
  REQUIRED("required"),
  CONDITIONAL("conditional"),
  OPTIONAL("optional"),
  FORBIDDEN("forbidden");

  private final String wireValue;

  RequestFieldPresence(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this request-field presence. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable request-field presence value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(RequestFieldPresence.class);
  }

  /** Parses one stable request-field presence value. */
  public static RequestFieldPresence fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        RequestFieldPresence.class, wireValue, "Unsupported request-field presence");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
