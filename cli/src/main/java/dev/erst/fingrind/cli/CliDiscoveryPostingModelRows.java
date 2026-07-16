package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Builds ordered posting-model help rows for command-scoped and plan-scoped discovery output. */
final class CliDiscoveryPostingModelRows {
  private static final List<CanonicalNestedFieldGroup> CANONICAL_NESTED_FIELD_GROUPS =
      List.of(
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.LINES,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::lineFields,
              "lines[]."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::openingBalanceFields,
              "openingBalances[]."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.RECOGNITION_INTERVAL,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor
                  ::recognitionIntervalFields,
              "recognitionInterval."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::foreignExchangeFields,
              "foreignExchange."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::quotedRateFields,
              "foreignExchange.quotedRate."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.TAX,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::taxFields,
              "tax."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.EVIDENCE,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::evidenceFields,
              "evidence."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.EVIDENCE,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::sourceDocumentFields,
              "evidence.sourceDocuments[]."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.EVIDENCE,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::approvalFields,
              "evidence.approvals[]."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.PROVENANCE,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::provenanceFields,
              "provenance."),
          new CanonicalNestedFieldGroup(
              ProtocolPostEntryFields.TopLevel.REVERSAL,
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor::reversalFields,
              "reversal."));

  private CliDiscoveryPostingModelRows() {}

  static List<List<String>> allRows(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
      String prefix) {
    ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postEntryShape, postingTemplate);
    List<List<String>> rows = new ArrayList<>();
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.topLevelFields(),
        prefix,
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.lineFields(),
        prefix + "lines[].",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.openingBalanceFields(),
        prefix + "openingBalances[].",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.recognitionIntervalFields(),
        prefix + "recognitionInterval.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.foreignExchangeFields(),
        prefix + "foreignExchange.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.quotedRateFields(),
        prefix + "foreignExchange.quotedRate.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.taxFields(),
        prefix + "tax.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.evidenceFields(),
        prefix + "evidence.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.sourceDocumentFields(),
        prefix + "evidence.sourceDocuments[].",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.approvalFields(),
        prefix + "evidence.approvals[].",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.provenanceFields(),
        prefix + "provenance.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    CliDiscoveryPostingModelRowSupport.appendPostingRows(
        rows,
        postEntryShape.reversalFields(),
        prefix + "reversal.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    return List.copyOf(rows);
  }

  static List<List<String>> canonicalRows(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
      String prefix) {
    ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postEntryShape, postingTemplate);
    List<List<String>> rows = new ArrayList<>();
    CliDiscoveryPostingModelRowSupport.appendTopLevelRows(
        rows,
        postEntryShape.topLevelFields(),
        prefix,
        postEntryShape,
        postingTemplate,
        selectedEntryKind);
    for (CanonicalNestedFieldGroup group : CANONICAL_NESTED_FIELD_GROUPS) {
      if (!CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
          group.topLevelField(), postingTemplate, selectedEntryKind)) {
        continue;
      }
      CliDiscoveryPostingModelRowSupport.appendPostingRows(
          rows,
          group.fields().apply(postEntryShape),
          prefix + group.prefixSuffix(),
          postEntryShape,
          postingTemplate,
          selectedEntryKind);
    }
    return List.copyOf(rows);
  }

  static List<List<String>> supplementalRows(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
      ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
      String prefix) {
    ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKindOrPublishedFallback(
            postEntryShape, postingTemplate);
    List<List<String>> rows = new ArrayList<>();
    CliDiscoveryPostingModelRowSupport.appendSupplementalTopLevelRows(
        rows,
        postEntryShape.topLevelFields(),
        prefix,
        postEntryShape,
        postingTemplate,
        selectedEntryKind,
        ProtocolPostEntryFields.topLevelFields());
    CliDiscoveryPostingModelRowSupport.appendPublishedPostingRows(
        rows,
        postEntryShape.lineFields(),
        ProtocolPostEntryFields.journalLineFields(),
        prefix + "lines[].",
        postEntryShape,
        postingTemplate,
        selectedEntryKind,
        !CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.LINES, postingTemplate, selectedEntryKind));
    CliDiscoveryPostingModelRowSupport.appendPublishedPostingRows(
        rows,
        postEntryShape.openingBalanceFields(),
        ProtocolPostEntryFields.openingBalanceFields(),
        prefix + "openingBalances[].",
        postEntryShape,
        postingTemplate,
        selectedEntryKind,
        !CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.OPENING_BALANCES, postingTemplate, selectedEntryKind));
    CliDiscoveryPostingModelRowSupport.appendPublishedPostingRows(
        rows,
        postEntryShape.recognitionIntervalFields(),
        ProtocolPostEntryFields.recognitionIntervalFields(),
        prefix + "recognitionInterval.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind,
        !CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.RECOGNITION_INTERVAL,
            postingTemplate,
            selectedEntryKind));
    CliDiscoveryPostingModelRowSupport.appendPublishedPostingRows(
        rows,
        postEntryShape.foreignExchangeFields(),
        ProtocolPostEntryFields.foreignExchangeFields(),
        prefix + "foreignExchange.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind,
        !CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE, postingTemplate, selectedEntryKind));
    CliDiscoveryPostingModelRowSupport.appendPublishedPostingRows(
        rows,
        postEntryShape.quotedRateFields(),
        ProtocolPostEntryFields.quotedRateFields(),
        prefix + "foreignExchange.quotedRate.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind,
        !CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE, postingTemplate, selectedEntryKind));
    CliDiscoveryPostingModelRowSupport.appendPublishedPostingRows(
        rows,
        postEntryShape.taxFields(),
        ProtocolPostEntryFields.taxFields(),
        prefix + "tax.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind,
        !CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.TAX, postingTemplate, selectedEntryKind));
    CliDiscoveryPostingModelRowSupport.appendPublishedPostingRows(
        rows,
        postEntryShape.reversalFields(),
        ProtocolPostEntryFields.reversalFields(),
        prefix + "reversal.",
        postEntryShape,
        postingTemplate,
        selectedEntryKind,
        !CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.REVERSAL, postingTemplate, selectedEntryKind));
    return List.copyOf(rows);
  }

  private record CanonicalNestedFieldGroup(
      String topLevelField,
      Function<
              ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor,
              List<ContractRequestShapes.RequestFieldDescriptor>>
          fields,
      String prefixSuffix) {}
}
