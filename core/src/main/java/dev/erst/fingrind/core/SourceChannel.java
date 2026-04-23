package dev.erst.fingrind.core;

import java.util.List;

/** Operating surface through which one posting request entered FinGrind. */
public enum SourceChannel implements WireValue {
  CLI;

  /** Returns the stable public wire value for this source channel. */
  @Override
  public String wireValue() {
    return switch (this) {
      case CLI -> "CLI";
    };
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
