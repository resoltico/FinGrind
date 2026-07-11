package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical post-entry request-field sets owned by inventory operations. */
public final class ProtocolInventoryPostingRequestFieldSets {
  private ProtocolInventoryPostingRequestFieldSets() {}

  /** Returns accepted fields for a settled inventory acquisition. */
  public static Set<String> purchaseSettledFields() {
    return ProtocolPostingRequestFieldSets.PURCHASE_SETTLED_FIELDS;
  }

  /** Returns accepted fields for a credit inventory acquisition. */
  public static Set<String> purchaseOnCreditFields() {
    return ProtocolPostingRequestFieldSets.PURCHASE_ON_CREDIT_FIELDS;
  }

  /** Returns accepted fields for settled inventory capitalization. */
  public static Set<String> inventoryCapitalizationSettledFields() {
    return ProtocolPostingRequestFieldSets.INVENTORY_CAPITALIZATION_SETTLED_FIELDS;
  }

  /** Returns accepted fields for credit inventory capitalization. */
  public static Set<String> inventoryCapitalizationOnCreditFields() {
    return ProtocolPostingRequestFieldSets.INVENTORY_CAPITALIZATION_ON_CREDIT_FIELDS;
  }

  /** Returns accepted fields for an inventory carrying-cost write-down. */
  public static Set<String> inventoryWriteDownFields() {
    return ProtocolPostingRequestFieldSets.INVENTORY_WRITE_DOWN_FIELDS;
  }

  /** Returns accepted fields for an inventory quantity shrinkage. */
  public static Set<String> inventoryShrinkageFields() {
    return ProtocolPostingRequestFieldSets.INVENTORY_SHRINKAGE_FIELDS;
  }

  /** Returns accepted fields for an inventory quantity count increase. */
  public static Set<String> inventoryCountIncreaseFields() {
    return ProtocolPostingRequestFieldSets.INVENTORY_COUNT_INCREASE_FIELDS;
  }
}
