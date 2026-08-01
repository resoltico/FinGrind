package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;

/** Owns posting-model descriptor validation and the row descriptions shown in command help. */
final class CliDiscoveryPostingFieldDescriptions {
  private CliDiscoveryPostingFieldDescriptions() {}

  static ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    validatePostingModelDescriptorShape(postEntryShape);
    return requiredEntryKindSemantics(postEntryShape, postingTemplate.entryKind().wireValue());
  }

  static ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKindOrPublishedFallback(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    validatePostingModelDescriptorShape(postEntryShape);
    return postEntryShape.entryKindSemantics().stream()
        .filter(entryKind -> entryKind.entryKind() == postingTemplate.entryKind())
        .findFirst()
        .orElseGet(
            () ->
                publishedEntryKindSemantics(
                    ProtocolCatalog.domain()
                        .requestSurface()
                        .bookkeepingEntryKind(postingTemplate.entryKind())));
  }

  static String describePostingField(
      ContractRequestShapes.RequestFieldDescriptor field,
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate,
      ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind,
      boolean filterToSelectedEntryKind) {
    if ("entryKind".equals(field.name())) {
      List<String> publishedEntryKinds =
          filterToSelectedEntryKind
              ? List.of(selectedEntryKind.entryKind().wireValue())
              : postEntryShape.entryKindSemantics().stream()
                  .map(entry -> entry.entryKind().wireValue())
                  .toList();
      return "Canonical scaffold value: "
          + postingTemplate.entryKind().wireValue()
          + ". "
          + field.description()
          + " Published entry kinds: "
          + CliTextFormat.joined(publishedEntryKinds)
          + ".";
    }
    if (ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_TYPE.equals(field.name())) {
      return field.description()
          + " Canonical scaffold value: "
          + postingTemplate.evidence().sourceDocuments().getFirst().sourceDocumentType()
          + ".";
    }
    return field.description();
  }

  private static void validatePostingModelDescriptorShape(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape) {
    requirePostingField(postEntryShape.topLevelFields(), "entryKind");
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

  private static ContractRequestShapes.EntryKindSemanticsDescriptor requiredEntryKindSemantics(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      String entryKindWireValue) {
    return postEntryShape.entryKindSemantics().stream()
        .filter(entryKind -> entryKind.entryKind().wireValue().equals(entryKindWireValue))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Help descriptor is missing entry-kind semantics for '%s'."
                        .formatted(entryKindWireValue)));
  }

  private static ContractRequestShapes.EntryKindSemanticsDescriptor publishedEntryKindSemantics(
      RequestSurfaceFacts.BookkeepingEntryKindFacts facts) {
    BookkeepingEntryKind entryKind = facts.entryKind();
    return new ContractRequestShapes.EntryKindSemanticsDescriptor(
        entryKind,
        facts.requiredTopLevelFields(),
        facts.optionalTopLevelFields(),
        facts.forbiddenTopLevelFields(),
        List.of(),
        facts.requiredSourceDocumentFields(),
        facts.sourceDocumentTypes().mode().wireValue(),
        facts.sourceDocumentTypes().acceptedValues(),
        facts.sourceDocumentTypes().semantics(),
        facts.semantics());
  }
}
