package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
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
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
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
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
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
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
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
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
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
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
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
    if (ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE.equals(fieldName)
        || ProtocolPostEntryFields.TopLevel.TAX.equals(fieldName)) {
      return true;
    }
    return templatePublishesField(fieldName, postingTemplate);
  }

  private static boolean isAlwaysPublishedField(String fieldName) {
    return ProtocolPostEntryFields.TopLevel.ENTRY_KIND.equals(fieldName)
        || ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE.equals(fieldName);
  }

  private static boolean isConditionallyPublishedField(
      String fieldName, BookkeepingEntryKind entryKind) {
    return ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF.equals(fieldName)
        && (entryKind == BookkeepingEntryKind.SALE_SETTLED
            || entryKind == BookkeepingEntryKind.SALE_ON_CREDIT);
  }

  private static boolean templatePublishesField(
      String fieldName, ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    if (templatePublishesRoleAccountField(fieldName, postingTemplate)) {
      return true;
    }
    return switch (fieldName) {
      case ProtocolPostEntryFields.TopLevel.AMOUNT -> postingTemplate.amount() != null;
      case ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE ->
          postingTemplate.foreignExchange() != null;
      case ProtocolPostEntryFields.TopLevel.TAX -> postingTemplate.tax() != null;
      case ProtocolPostEntryFields.TopLevel.LINES -> postingTemplate.lines() != null;
      case ProtocolPostEntryFields.TopLevel.OPENING_BALANCES ->
          postingTemplate.openingBalances() != null;
      case ProtocolPostEntryFields.TopLevel.EVIDENCE -> true;
      case ProtocolPostEntryFields.TopLevel.PROVENANCE -> true;
      case ProtocolPostEntryFields.TopLevel.REVERSAL -> postingTemplate.reversal() != null;
      default -> false;
    };
  }

  private static boolean templatePublishesRoleAccountField(
      String fieldName, ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    return switch (fieldName) {
      case ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE ->
          postingTemplate.cashAccountCode() != null;
      case ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE ->
          postingTemplate.revenueAccountCode() != null;
      case ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE ->
          postingTemplate.expenseAccountCode() != null;
      case ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE ->
          postingTemplate.equityAccountCode() != null;
      default -> false;
    };
  }

  private static Optional<ContractRequestShapes.RequestFieldDescriptor> findField(
      List<ContractRequestShapes.RequestFieldDescriptor> fields, String fieldName) {
    return fields.stream().filter(field -> field.name().equals(fieldName)).findFirst();
  }
}
