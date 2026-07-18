package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical post-entry request-field sets owned by inventory operations. */
public final class ProtocolInventoryPostingRequestFieldSets {
  private static final Set<String> PURCHASE_SETTLED_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Inventory.QUANTITY,
          ProtocolBusinessEventFields.Inventory.UNIT_COST,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.TAX);
  private static final Set<String> PURCHASE_ON_CREDIT_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.PAYABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Inventory.QUANTITY,
          ProtocolBusinessEventFields.Inventory.UNIT_COST,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.TAX);
  private static final Set<String> CAPITALIZATION_SETTLED_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.TAX);
  private static final Set<String> CAPITALIZATION_ON_CREDIT_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.PAYABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.TAX);
  private static final Set<String> WRITE_DOWN_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Inventory.WRITE_DOWN_LOSS_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT);
  private static final Set<String> SHRINKAGE_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Inventory.SHRINKAGE_LOSS_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Inventory.QUANTITY);
  private static final Set<String> COUNT_INCREASE_FIELDS =
      ProtocolPostingRequestFieldSetSupport.typedEntryFields(
          ProtocolBusinessEventFields.Inventory.INVENTORY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Inventory.COUNT_GAIN_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Inventory.QUANTITY,
          ProtocolBusinessEventFields.Inventory.UNIT_COST);

  private ProtocolInventoryPostingRequestFieldSets() {}

  /** Returns accepted fields for a settled inventory acquisition. */
  public static Set<String> purchaseSettledFields() {
    return PURCHASE_SETTLED_FIELDS;
  }

  /** Returns accepted fields for a credit inventory acquisition. */
  public static Set<String> purchaseOnCreditFields() {
    return PURCHASE_ON_CREDIT_FIELDS;
  }

  /** Returns accepted fields for settled inventory capitalization. */
  public static Set<String> inventoryCapitalizationSettledFields() {
    return CAPITALIZATION_SETTLED_FIELDS;
  }

  /** Returns accepted fields for credit inventory capitalization. */
  public static Set<String> inventoryCapitalizationOnCreditFields() {
    return CAPITALIZATION_ON_CREDIT_FIELDS;
  }

  /** Returns accepted fields for an inventory carrying-cost write-down. */
  public static Set<String> inventoryWriteDownFields() {
    return WRITE_DOWN_FIELDS;
  }

  /** Returns accepted fields for an inventory quantity shrinkage. */
  public static Set<String> inventoryShrinkageFields() {
    return SHRINKAGE_FIELDS;
  }

  /** Returns accepted fields for an inventory quantity count increase. */
  public static Set<String> inventoryCountIncreaseFields() {
    return COUNT_INCREASE_FIELDS;
  }
}
