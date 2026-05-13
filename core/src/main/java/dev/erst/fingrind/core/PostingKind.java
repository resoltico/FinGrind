package dev.erst.fingrind.core;

import java.util.List;

/** Canonical kinds for durable postings committed into one protected FinGrind book. */
public enum PostingKind implements WireValue {
  STANDARD,
  PERIOD_CLOSE;

  @Override
  public String wireValue() {
    return switch (this) {
      case STANDARD -> "STANDARD";
      case PERIOD_CLOSE -> "PERIOD_CLOSE";
    };
  }

  /** Returns every stable wire value for durable posting kinds. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PostingKind.class);
  }

  /** Parses one stable posting-kind wire value. */
  public static PostingKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(PostingKind.class, wireValue, "Unsupported postingKind");
  }

  /** Returns whether this posting is one ordinary business posting rather than a close entry. */
  public boolean isStandard() {
    return this == STANDARD;
  }
}
