package dev.erst.fingrind.core;

import java.util.List;

/** Canonical legal-form posture for one protected book. */
public enum EntityForm implements WireValue {
  OWNER_MANAGED_SINGLE_ENTITY;

  @Override
  public String wireValue() {
    return switch (this) {
      case OWNER_MANAGED_SINGLE_ENTITY -> "OWNER_MANAGED_SINGLE_ENTITY";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(EntityForm.class);
  }

  /** Parses one stable wire value. */
  public static EntityForm fromWireValue(String wireValue) {
    return WireValue.fromWireValue(EntityForm.class, wireValue, "Unsupported entityForm");
  }
}
