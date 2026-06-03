package dev.erst.fingrind.core;

import java.util.List;

/** Canonical book-template identifiers for guided owner-managed setup. */
public enum BookTemplateId implements WireValue {
  OWNER_MANAGED_SERVICE_CASH;

  @Override
  public String wireValue() {
    return switch (this) {
      case OWNER_MANAGED_SERVICE_CASH -> "OWNER_MANAGED_SERVICE_CASH";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookTemplateId.class);
  }

  /** Parses one stable wire value. */
  public static BookTemplateId fromWireValue(String wireValue) {
    return WireValue.fromWireValue(BookTemplateId.class, wireValue, "Unsupported bookTemplateId");
  }
}
