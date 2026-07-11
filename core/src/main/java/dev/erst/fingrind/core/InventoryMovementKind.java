package dev.erst.fingrind.core;

import java.util.List;

/** Durable inventory-movement vocabulary for the inventory subledger. */
public enum InventoryMovementKind implements WireValue {
  ACQUISITION,
  CAPITALIZATION,
  COUNT_INCREASE,
  OPENING,
  DISPOSAL,
  WRITE_DOWN,
  SHRINKAGE,
  REVERSAL_COMP;

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(InventoryMovementKind.class);
  }

  /** Parses one stable public wire value. */
  public static InventoryMovementKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        InventoryMovementKind.class, wireValue, "Unsupported inventoryMovementKind");
  }
}
