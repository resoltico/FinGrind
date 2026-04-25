package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable public cipher identifiers for protected FinGrind books. */
public enum BookCipher implements WireValue {
  CHACHA20("chacha20");

  private final String wireValue;

  BookCipher(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this book cipher. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public book-cipher identifier. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookCipher.class);
  }

  /** Parses one stable public book-cipher identifier. */
  public static BookCipher fromWireValue(String wireValue) {
    return WireValue.fromWireValue(BookCipher.class, wireValue, "Unsupported book cipher");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
