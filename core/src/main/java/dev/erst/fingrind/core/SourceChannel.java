package dev.erst.fingrind.core;

import java.util.List;

/** Stable committed-entry origin channel vocabulary for one public FinGrind line. */
public enum SourceChannel implements WireValue {
  CLI("CLI"),
  SYSTEM("SYSTEM");

  private final String wireValue;

  SourceChannel(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the stable public wire value for this source channel. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(SourceChannel.class);
  }

  /** Parses one stable public wire value. */
  public static SourceChannel fromWireValue(String wireValue) {
    return WireValue.fromWireValue(SourceChannel.class, wireValue, "Unsupported sourceChannel");
  }
}
