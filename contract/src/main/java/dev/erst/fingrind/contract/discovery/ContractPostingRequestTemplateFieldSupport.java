package dev.erst.fingrind.contract.discovery;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared field-level validation support for posting-template variant validators. */
final class ContractPostingRequestTemplateFieldSupport {
  private ContractPostingRequestTemplateFieldSupport() {}

  static void requireTextFields(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      TemplateTextField[] requiredFields) {
    for (TemplateTextField field : requiredFields) {
      ContractPostingTemplateFieldRules.requireText(field.value(fields), field.fieldName());
    }
  }

  static void requireTextFields(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      List<TemplateTextField> requiredFields) {
    for (TemplateTextField field : requiredFields) {
      ContractPostingTemplateFieldRules.requireText(field.value(fields), field.fieldName());
    }
  }

  static void forbidTextFields(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      TemplateTextField... forbiddenFields) {
    for (TemplateTextField field : forbiddenFields) {
      ContractPostingTemplateFieldRules.forbidText(field.value(fields), field.fieldName());
    }
  }

  static void forbidTextFields(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      List<TemplateTextField> forbiddenFields) {
    for (TemplateTextField field : forbiddenFields) {
      ContractPostingTemplateFieldRules.forbidText(field.value(fields), field.fieldName());
    }
  }

  static void forbidTextFieldsExcept(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields,
      List<TemplateTextField> admittedFields) {
    for (TemplateTextField field : TemplateTextField.values()) {
      if (!admittedFields.contains(field)) {
        ContractPostingTemplateFieldRules.forbidText(field.value(fields), field.fieldName());
      }
    }
  }

  static void requireNoSettlementAdjunct(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields, String owner) {
    if (fields.settlementAdjunct() != null) {
      throw new IllegalArgumentException("settlementAdjunct must be absent for " + owner + ".");
    }
  }

  static void forbidLinesAndOpeningBalances(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields) {
    ContractPostingTemplateFieldRules.forbidLines(fields.lines());
    ContractPostingTemplateFieldRules.forbidOpeningBalances(fields.openingBalances());
  }

  static void forbidInventoryMaintenanceFields(
      ContractPostingRequestTemplateValidators.PostingTemplateFields fields, String owner) {
    if (owner.startsWith("inventory")) {
      return;
    }
    forbidTextFields(
        fields,
        TemplateTextField.WRITE_DOWN_LOSS,
        TemplateTextField.SHRINKAGE_LOSS,
        TemplateTextField.COUNT_GAIN);
  }

  static void validateInventoryRelief(
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief,
      String owner,
      InventoryReliefPolicy policy) {
    if (policy == InventoryReliefPolicy.OPTIONAL) {
      validateOptionalInventoryRelief(inventoryRelief, owner);
    } else {
      forbidInventoryRelief(inventoryRelief, owner);
    }
  }

  private static void validateOptionalInventoryRelief(
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief, String owner) {
    if (inventoryRelief == null) {
      return;
    }
    if (inventoryRelief.inventoryAccountCode().equals(inventoryRelief.costOfSalesAccountCode())) {
      throw new IllegalArgumentException(
          owner
              + " inventoryRelief requires distinct inventoryAccountCode and costOfSalesAccountCode.");
    }
  }

  private static void forbidInventoryRelief(
      @Nullable InventoryReliefTemplateDescriptor inventoryRelief, String owner) {
    if (inventoryRelief != null) {
      throw new IllegalArgumentException("inventoryRelief must be absent for " + owner + ".");
    }
  }

  /** Inventory-relief policy for one posting-template kind. */
  enum InventoryReliefPolicy {
    OPTIONAL,
    FORBIDDEN
  }

  /** Canonical top-level text account fields owned by posting-template validation. */
  enum TemplateTextField {
    CASH("cashAccountCode"),
    RECEIVABLE("receivableAccountCode"),
    PAYABLE("payableAccountCode"),
    REVENUE("revenueAccountCode"),
    INVENTORY("inventoryAccountCode"),
    EXPENSE("expenseAccountCode"),
    WRITE_DOWN_LOSS("writeDownLossAccountCode"),
    SHRINKAGE_LOSS("shrinkageLossAccountCode"),
    COUNT_GAIN("countGainAccountCode"),
    EQUITY("equityAccountCode");

    private final String fieldName;

    TemplateTextField(String fieldName) {
      this.fieldName = fieldName;
    }

    String fieldName() {
      return fieldName;
    }

    @Nullable String value(ContractPostingRequestTemplateValidators.PostingTemplateFields fields) {
      return switch (this) {
        case CASH -> fields.cashAccountCode();
        case RECEIVABLE -> fields.receivableAccountCode();
        case PAYABLE -> fields.payableAccountCode();
        case REVENUE -> fields.revenueAccountCode();
        case INVENTORY -> fields.inventoryAccountCode();
        case EXPENSE -> fields.expenseAccountCode();
        case WRITE_DOWN_LOSS -> fields.writeDownLossAccountCode();
        case SHRINKAGE_LOSS -> fields.shrinkageLossAccountCode();
        case COUNT_GAIN -> fields.countGainAccountCode();
        case EQUITY -> fields.equityAccountCode();
      };
    }
  }
}
