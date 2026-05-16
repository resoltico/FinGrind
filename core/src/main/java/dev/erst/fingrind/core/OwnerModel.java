package dev.erst.fingrind.core;

import java.util.List;

/** Canonical ownership model vocabulary for one accounting entity profile. */
public enum OwnerModel implements WireValue {
  SOLE_OWNER,
  MULTI_OWNER,
  MEMBERSHIP_BODY,
  NO_PRIVATE_OWNER,
  UNKNOWN;

  @Override
  public String wireValue() {
    return switch (this) {
      case SOLE_OWNER -> "SOLE_OWNER";
      case MULTI_OWNER -> "MULTI_OWNER";
      case MEMBERSHIP_BODY -> "MEMBERSHIP_BODY";
      case NO_PRIVATE_OWNER -> "NO_PRIVATE_OWNER";
      case UNKNOWN -> "UNKNOWN";
    };
  }

  /** Returns every stable owner-model wire value. */
  public static List<String> wireValues() {
    return WireValue.wireValues(OwnerModel.class);
  }

  /** Parses one stable owner-model wire value. */
  public static OwnerModel fromWireValue(String wireValue) {
    return WireValue.fromWireValue(OwnerModel.class, wireValue, "Unsupported ownerModel");
  }
}
