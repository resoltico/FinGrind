package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Optional;

/** Shared row-building helpers for command-scoped and plan-scoped posting-model discovery text. */
final class CliDiscoveryPostingModelRowSupport {
  private CliDiscoveryPostingModelRowSupport() {}

  static void appendTopLevelRows(
      List<List<String>> rows,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String prefix,
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate,
      ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind) {
    for (String fieldName : ProtocolPostEntryFields.topLevelFields()) {
      ContractRequestShapes.RequestFieldDescriptor field =
          findField(fields, fieldName).orElse(null);
      if (field == null || field.presence() == RequestFieldPresence.FORBIDDEN) {
        continue;
      }
      boolean selected =
          includesCanonicalTopLevelField(fieldName, postingTemplate, selectedEntryKind);
      if (!selected) {
        continue;
      }
      rows.add(
          List.of(
              prefix + fieldName,
              CliDiscoveryPostingFieldDescriptions.describePostingField(
                  field, postEntryShape, postingTemplate, selectedEntryKind, selected)));
    }
  }

  static void appendSupplementalTopLevelRows(
      List<List<String>> rows,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String prefix,
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate,
      ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind,
      List<String> publishedFieldNames) {
    for (String fieldName : publishedFieldNames) {
      if (includesCanonicalTopLevelField(fieldName, postingTemplate, selectedEntryKind)) {
        continue;
      }
      findField(fields, fieldName)
          .ifPresent(
              field ->
                  rows.add(
                      List.of(
                          prefix + field.name(),
                          CliDiscoveryPostingFieldDescriptions.describePostingField(
                              field, postEntryShape, postingTemplate, selectedEntryKind, false))));
    }
  }

  static void appendPostingRows(
      List<List<String>> rows,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String prefix,
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate,
      ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind) {
    for (ContractRequestShapes.RequestFieldDescriptor field : fields) {
      if (field.presence() == RequestFieldPresence.FORBIDDEN) {
        continue;
      }
      rows.add(
          List.of(
              prefix + field.name(),
              CliDiscoveryPostingFieldDescriptions.describePostingField(
                  field, postEntryShape, postingTemplate, selectedEntryKind, false)));
    }
  }

  static void appendPublishedPostingRows(
      List<List<String>> rows,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      List<String> publishedFieldNames,
      String prefix,
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate,
      ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind,
      boolean includeFieldGroup) {
    if (!includeFieldGroup) {
      return;
    }
    for (String fieldName : publishedFieldNames) {
      findField(fields, fieldName)
          .ifPresent(
              field ->
                  rows.add(
                      List.of(
                          prefix + field.name(),
                          CliDiscoveryPostingFieldDescriptions.describePostingField(
                              field, postEntryShape, postingTemplate, selectedEntryKind, false))));
    }
  }

  static boolean includesCanonicalTopLevelField(
      String fieldName,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate,
      ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind) {
    if (isAlwaysPublishedField(fieldName)) {
      return true;
    }
    if (isConditionallyPublishedField(fieldName, selectedEntryKind.entryKind())) {
      return true;
    }
    if (!selectedEntryKind.requiredTopLevelFields().contains(fieldName)
        && !selectedEntryKind.optionalTopLevelFields().contains(fieldName)) {
      return false;
    }
    if (ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE.equals(fieldName)
        || ProtocolBusinessEventFields.Core.TAX.equals(fieldName)) {
      return true;
    }
    return templatePublishesField(fieldName, postingTemplate);
  }

  private static boolean isAlwaysPublishedField(String fieldName) {
    return ProtocolBusinessEventFields.Core.ENTRY_KIND.equals(fieldName)
        || ProtocolBusinessEventFields.Core.EFFECTIVE_DATE.equals(fieldName);
  }

  private static boolean isConditionallyPublishedField(
      String fieldName, BookkeepingEntryKind entryKind) {
    return ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF.equals(fieldName)
        && (entryKind == BookkeepingEntryKind.SALE_SETTLED
            || entryKind == BookkeepingEntryKind.SALE_ON_CREDIT);
  }

  private static boolean templatePublishesField(
      String fieldName,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    if (templatePublishesRoleAccountField(fieldName, postingTemplate)) {
      return true;
    }
    if (templatePublishesAccrualCutoffField(fieldName, postingTemplate)) {
      return true;
    }
    return switch (fieldName) {
      case ProtocolBusinessEventFields.Core.AMOUNT -> postingTemplate.amount() != null;
      case ProtocolBusinessEventFields.Inventory.QUANTITY -> postingTemplate.quantity() != null;
      case ProtocolBusinessEventFields.Inventory.UNIT_COST -> postingTemplate.unitCost() != null;
      case ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE ->
          postingTemplate.foreignExchange() != null;
      case ProtocolBusinessEventFields.Core.TAX -> postingTemplate.tax() != null;
      case ProtocolBusinessEventFields.Core.LINES -> postingTemplate.lines() != null;
      case ProtocolBusinessEventFields.Core.OPENING_BALANCES ->
          postingTemplate.openingBalances() != null;
      default -> false;
    };
  }

  private static boolean templatePublishesAccrualCutoffField(
      String fieldName,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    return switch (fieldName) {
      case ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID ->
          postingTemplate.accrualCutoffId() != null;
      case ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL ->
          postingTemplate.recognitionInterval() != null;
      case ProtocolBusinessEventFields.Core.EVIDENCE, ProtocolBusinessEventFields.Core.PROVENANCE ->
          true;
      case ProtocolBusinessEventFields.Core.REVERSAL -> postingTemplate.reversal() != null;
      default -> false;
    };
  }

  private static boolean templatePublishesRoleAccountField(
      String fieldName,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    return switch (fieldName) {
      case ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE ->
          postingTemplate.cashAccountCode() != null;
      case ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE ->
          postingTemplate.revenueAccountCode() != null;
      case ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE ->
          postingTemplate.expenseAccountCode() != null;
      case ProtocolBusinessEventFields.AccrualCutoff.PREPAYMENT_ASSET_ACCOUNT_CODE ->
          postingTemplate.prepaymentAssetAccountCode() != null;
      case ProtocolBusinessEventFields.AccrualCutoff.DEFERRED_REVENUE_ACCOUNT_CODE ->
          postingTemplate.deferredRevenueAccountCode() != null;
      case ProtocolBusinessEventFields.AccrualCutoff.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE ->
          postingTemplate.accruedExpenseLiabilityAccountCode() != null;
      case ProtocolBusinessEventFields.Core.EQUITY_ACCOUNT_CODE ->
          postingTemplate.equityAccountCode() != null;
      default -> false;
    };
  }

  private static Optional<ContractRequestShapes.RequestFieldDescriptor> findField(
      List<ContractRequestShapes.RequestFieldDescriptor> fields, String fieldName) {
    return fields.stream().filter(field -> field.name().equals(fieldName)).findFirst();
  }
}
