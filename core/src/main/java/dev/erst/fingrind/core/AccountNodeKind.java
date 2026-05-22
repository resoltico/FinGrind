package dev.erst.fingrind.core;

import java.util.List;

/** Canonical chart-node role for one declared account inside the hierarchy. */
public enum AccountNodeKind implements WireValue {
  HEADER,
  POSTABLE;

  /** Returns whether postings may target this node directly. */
  public boolean allowsPosting() {
    return this == POSTABLE;
  }

  /** Returns whether this node may own child accounts. */
  public boolean allowsChildren() {
    return this == HEADER;
  }

  /** Returns the stable public wire value for this node kind. */
  @Override
  public String wireValue() {
    return switch (this) {
      case HEADER -> "HEADER";
      case POSTABLE -> "POSTABLE";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccountNodeKind.class);
  }

  /** Parses one stable public wire value. */
  public static AccountNodeKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(AccountNodeKind.class, wireValue, "Unsupported accountNodeKind");
  }
}
