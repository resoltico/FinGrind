package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import java.util.ArrayList;
import java.util.List;

/** Builds ordered posting-model help rows for command-scoped and plan-scoped discovery output. */
final class CliDiscoveryPostingModelRows {
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
    if (CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
        ProtocolPostEntryFields.TopLevel.LINES, postingTemplate, selectedEntryKind)) {
      CliDiscoveryPostingModelRowSupport.appendPostingRows(
          rows,
          postEntryShape.lineFields(),
          prefix + "lines[].",
          postEntryShape,
          postingTemplate,
          selectedEntryKind);
    }
    if (CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
        ProtocolPostEntryFields.TopLevel.OPENING_BALANCES, postingTemplate, selectedEntryKind)) {
      CliDiscoveryPostingModelRowSupport.appendPostingRows(
          rows,
          postEntryShape.openingBalanceFields(),
          prefix + "openingBalances[].",
          postEntryShape,
          postingTemplate,
          selectedEntryKind);
    }
    if (CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
        ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE, postingTemplate, selectedEntryKind)) {
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
    }
    if (CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
        ProtocolPostEntryFields.TopLevel.TAX, postingTemplate, selectedEntryKind)) {
      CliDiscoveryPostingModelRowSupport.appendPostingRows(
          rows,
          postEntryShape.taxFields(),
          prefix + "tax.",
          postEntryShape,
          postingTemplate,
          selectedEntryKind);
    }
    if (CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
        ProtocolPostEntryFields.TopLevel.EVIDENCE, postingTemplate, selectedEntryKind)) {
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
    }
    if (CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
        ProtocolPostEntryFields.TopLevel.PROVENANCE, postingTemplate, selectedEntryKind)) {
      CliDiscoveryPostingModelRowSupport.appendPostingRows(
          rows,
          postEntryShape.provenanceFields(),
          prefix + "provenance.",
          postEntryShape,
          postingTemplate,
          selectedEntryKind);
    }
    if (CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
        ProtocolPostEntryFields.TopLevel.REVERSAL, postingTemplate, selectedEntryKind)) {
      CliDiscoveryPostingModelRowSupport.appendPostingRows(
          rows,
          postEntryShape.reversalFields(),
          prefix + "reversal.",
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
}
