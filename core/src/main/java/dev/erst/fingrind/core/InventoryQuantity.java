package dev.erst.fingrind.core;

/** Exact positive item quantity kept separately from money. */
public record InventoryQuantity(long units) {
  /** Validates one inventory quantity. */
  public InventoryQuantity {
    if (units <= 0L) {
      throw new IllegalArgumentException("Inventory quantity must be strictly positive.");
    }
  }
}
