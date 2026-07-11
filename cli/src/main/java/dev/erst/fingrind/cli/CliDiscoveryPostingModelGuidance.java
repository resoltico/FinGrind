package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import java.util.List;

/** Renders the shared posting-model help projection for direct and plan-nested entry surfaces. */
final class CliDiscoveryPostingModelGuidance {
  private CliDiscoveryPostingModelGuidance() {}

  static String renderPostingModel(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    return CliTextFormat.renderKeyValueBlock(
        postingModelRows(postEntryShape, postingTemplate, "", false),
        CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
  }

  static String renderEntrySemantics(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    return CliTextFormat.wrap(
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postEntryShape, postingTemplate)
            .semantics(),
        CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
  }

  static List<List<String>> postingModelRows(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
      String prefix) {
    return CliDiscoveryPostingModelRows.allRows(postEntryShape, postingTemplate, prefix);
  }

  static List<List<String>> postingModelRows(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
      String prefix,
      boolean scopeToCanonicalTemplate) {
    return scopeToCanonicalTemplate
        ? CliDiscoveryPostingModelRows.canonicalRows(postEntryShape, postingTemplate, prefix)
        : CliDiscoveryPostingModelRows.allRows(postEntryShape, postingTemplate, prefix);
  }

  static List<List<String>> supplementalPostingModelRows(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
      String prefix) {
    return CliDiscoveryPostingModelRows.supplementalRows(postEntryShape, postingTemplate, prefix);
  }
}
