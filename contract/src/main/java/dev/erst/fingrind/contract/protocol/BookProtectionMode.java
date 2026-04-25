package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable public protection requirements for book storage surfaces. */
public enum BookProtectionMode implements WireValue {
  REQUIRED("required");

  private final String wireValue;

  BookProtectionMode(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this book-protection mode. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public book-protection mode. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookProtectionMode.class);
  }

  /** Parses one stable public book-protection mode. */
  public static BookProtectionMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        BookProtectionMode.class, wireValue, "Unsupported book protection mode");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
