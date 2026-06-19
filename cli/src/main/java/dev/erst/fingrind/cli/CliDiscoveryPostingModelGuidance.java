package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import java.util.ArrayList;
import java.util.List;

/** Renders the shared posting-model help projection for direct and plan-nested entry surfaces. */
final class CliDiscoveryPostingModelGuidance {
  private CliDiscoveryPostingModelGuidance() {}

  static String renderPostingModel(
      ContractRequestShapes.PostEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    return CliTextFormat.renderKeyValueBlock(
        postingModelRows(postEntryShape, postingTemplate, ""),
        CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
  }

  static List<List<String>> postingModelRows(
      ContractRequestShapes.PostEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
      String prefix) {
    validatePostingModelDescriptorShape(postEntryShape);
    List<List<String>> rows = new ArrayList<>();
    appendPostingRows(
        rows, postEntryShape.topLevelFields(), prefix, postEntryShape, postingTemplate);
    appendPostingRows(
        rows, postEntryShape.lineFields(), prefix + "lines[].", postEntryShape, postingTemplate);
    appendPostingRows(
        rows,
        postEntryShape.openingBalanceFields(),
        prefix + "openingBalances[].",
        postEntryShape,
        postingTemplate);
    appendPostingRows(
        rows,
        postEntryShape.evidenceFields(),
        prefix + "evidence.",
        postEntryShape,
        postingTemplate);
    appendPostingRows(
        rows,
        postEntryShape.sourceDocumentFields(),
        prefix + "evidence.sourceDocuments[].",
        postEntryShape,
        postingTemplate);
    appendPostingRows(
        rows,
        postEntryShape.approvalFields(),
        prefix + "evidence.approvals[].",
        postEntryShape,
        postingTemplate);
    appendPostingRows(
        rows,
        postEntryShape.provenanceFields(),
        prefix + "provenance.",
        postEntryShape,
        postingTemplate);
    appendPostingRows(
        rows,
        postEntryShape.reversalFields(),
        prefix + "reversal.",
        postEntryShape,
        postingTemplate);
    return List.copyOf(rows);
  }

  private static void appendPostingRows(
      List<List<String>> rows,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String prefix,
      ContractRequestShapes.PostEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    for (ContractRequestShapes.RequestFieldDescriptor field : fields) {
      if (field.presence() == RequestFieldPresence.FORBIDDEN) {
        continue;
      }
      rows.add(
          List.of(
              prefix + field.name(), describePostingField(field, postEntryShape, postingTemplate)));
    }
  }

  private static String describePostingField(
      ContractRequestShapes.RequestFieldDescriptor field,
      ContractRequestShapes.PostEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    if ("entryKind".equals(field.name())) {
      return field.description()
          + " Canonical scaffold value: "
          + postingTemplate.entryKind().wireValue()
          + ".";
    }
    if ("recipeKind".equals(field.name())) {
      return field.description()
          + " Published shortcuts: "
          + CliTextFormat.joined(
              postEntryShape.journalRecipeSemantics().stream()
                  .map(recipe -> recipe.recipeKind().wireValue())
                  .toList())
          + ".";
    }
    return field.description();
  }

  private static void validatePostingModelDescriptorShape(
      ContractRequestShapes.PostEntryRequestShapeDescriptor postEntryShape) {
    requirePostingField(postEntryShape.topLevelFields(), "entryKind");
    requirePostingField(postEntryShape.topLevelFields(), "recipeKind");
  }

  private static ContractRequestShapes.RequestFieldDescriptor requirePostingField(
      List<ContractRequestShapes.RequestFieldDescriptor> fields, String fieldName) {
    return fields.stream()
        .filter(field -> field.name().equals(fieldName))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Help descriptor is missing posting-request field '%s'.".formatted(fieldName)));
  }
}
