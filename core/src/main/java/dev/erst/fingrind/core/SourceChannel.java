package dev.erst.fingrind.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Operating surface through which one posting request entered FinGrind. */
public enum SourceChannel {
  CLI;

  /** Returns the stable public wire value for this source channel. */
  public String wireValue() {
    return switch (this) {
      case CLI -> "CLI";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return Arrays.stream(values()).map(SourceChannel::wireValue).toList();
  }

  /** Parses one stable public wire value. */
  public static SourceChannel fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(value -> value.wireValue().equals(wireValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported sourceChannel: " + wireValue));
  }
}
