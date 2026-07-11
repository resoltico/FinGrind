package dev.erst.fingrind.core;

import java.util.List;

/** Canonical inventory-costing doctrine identifiers published by the shared kernel. */
public enum InventoryCostingDoctrine implements WireValue {
  WEIGHTED_AVERAGE;

  @Override
  public String wireValue() {
    return switch (this) {
      case WEIGHTED_AVERAGE -> "WEIGHTED_AVERAGE";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(InventoryCostingDoctrine.class);
  }

  /** Parses one stable wire value. */
  public static InventoryCostingDoctrine fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        InventoryCostingDoctrine.class, wireValue, "Unsupported inventoryCostingDoctrine");
  }
}
