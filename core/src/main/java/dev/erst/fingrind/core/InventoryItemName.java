package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical display name for one inventory item. */
public record InventoryItemName(String value) {
  /** Normalizes and validates one inventory-item name. */
  public InventoryItemName {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Inventory item name must not be blank.");
    }
    if (value.length() > 255) {
      throw new IllegalArgumentException("Inventory item name must not exceed 255 characters.");
    }
  }
}
