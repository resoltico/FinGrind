package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one inventory item tracked by business-event subledgers. */
public record InventoryItemId(String value) {
  /** Normalizes and validates one inventory-item identifier. */
  public InventoryItemId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Inventory item id must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Inventory item id must not exceed 128 characters.");
    }
  }
}
