package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;

/** Durable committed-entry surface identifier owned by the current public FinGrind line. */
public final class SourceChannel implements WireValue {
  public static final SourceChannel CLI = new SourceChannel("CLI");

  private static final List<SourceChannel> VALUES = List.of(CLI);

  private final String wireValue;

  private SourceChannel(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable public wire value for this source channel. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public source-channel instance in declaration order. */
  public static SourceChannel[] values() {
    return VALUES.toArray(SourceChannel[]::new);
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return VALUES.stream().map(SourceChannel::wireValue).toList();
  }

  /** Parses one stable public wire value. */
  public static SourceChannel fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    if (CLI.wireValue.equals(wireValue)) {
      return CLI;
    }
    throw new IllegalArgumentException("Unsupported sourceChannel: " + wireValue);
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
