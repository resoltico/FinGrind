package dev.erst.fingrind.core;

import java.util.List;

/** Canonical entity-form vocabulary used for accounting-policy selection. */
public enum EntityForm implements WireValue {
  FREELANCER,
  SOLE_PROPRIETORSHIP,
  COMPANY,
  PARTNERSHIP,
  NONPROFIT,
  BRANCH,
  OTHER;

  @Override
  public String wireValue() {
    return switch (this) {
      case FREELANCER -> "FREELANCER";
      case SOLE_PROPRIETORSHIP -> "SOLE_PROPRIETORSHIP";
      case COMPANY -> "COMPANY";
      case PARTNERSHIP -> "PARTNERSHIP";
      case NONPROFIT -> "NONPROFIT";
      case BRANCH -> "BRANCH";
      case OTHER -> "OTHER";
    };
  }

  /** Returns every stable entity-form wire value. */
  public static List<String> wireValues() {
    return WireValue.wireValues(EntityForm.class);
  }

  /** Parses one stable entity-form wire value. */
  public static EntityForm fromWireValue(String wireValue) {
    return WireValue.fromWireValue(EntityForm.class, wireValue, "Unsupported entityForm");
  }
}
