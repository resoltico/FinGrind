package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredArray;

import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingNestedFieldSets;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses evidence payloads for posting requests. */
final class CliAccountingEvidenceRequestParser {
  private CliAccountingEvidenceRequestParser() {}

  static AccountingEvidence readEvidence(ObjectNode evidenceNode) {
    List<SourceDocumentReference> sourceDocuments =
        readSourceDocuments(
            requiredArray(evidenceNode, ProtocolPostEntryFields.Evidence.SOURCE_DOCUMENTS));
    List<ApprovalReference> approvals =
        readApprovals(requiredArray(evidenceNode, ProtocolPostEntryFields.Evidence.APPROVALS));
    return new AccountingEvidence(sourceDocuments, approvals);
  }

  private static List<SourceDocumentReference> readSourceDocuments(JsonNode sourceDocumentsNode) {
    List<SourceDocumentReference> sourceDocuments = new ArrayList<>();
    int index = 0;
    for (JsonNode sourceDocumentNode : sourceDocumentsNode) {
      String context = ProtocolPostEntryFields.Evidence.SOURCE_DOCUMENTS + "[" + index + "]";
      ObjectNode sourceDocumentObject = requireObjectNode(sourceDocumentNode, context);
      rejectUnexpectedFields(
          sourceDocumentObject, context, ProtocolPostingNestedFieldSets.sourceDocumentFields());
      sourceDocuments.add(
          new SourceDocumentReference(
              new SourceDocumentId(
                  CliRequestPlaceholderValues.requiredRealText(
                      sourceDocumentObject,
                      ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_ID,
                      ScaffoldPlaceholders.SOURCE_DOCUMENT_ID,
                      context + ".")),
              new SourceDocumentType(
                  CliRequestPlaceholderValues.requiredRealText(
                      sourceDocumentObject,
                      ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_TYPE,
                      ScaffoldPlaceholders.SOURCE_DOCUMENT_TYPE,
                      context + ".")),
              CanonicalTemporalText.parseLocalDate(
                  CliRequestPlaceholderValues.requiredRealText(
                      sourceDocumentObject,
                      ProtocolPostEntryFields.SourceDocument.DOCUMENT_DATE,
                      ScaffoldPlaceholders.EFFECTIVE_DATE,
                      context + "."),
                  context + "." + ProtocolPostEntryFields.SourceDocument.DOCUMENT_DATE)));
      index++;
    }
    return List.copyOf(sourceDocuments);
  }

  private static List<ApprovalReference> readApprovals(JsonNode approvalsNode) {
    List<ApprovalReference> approvals = new ArrayList<>();
    int index = 0;
    for (JsonNode approvalNode : approvalsNode) {
      String context = ProtocolPostEntryFields.Evidence.APPROVALS + "[" + index + "]";
      ObjectNode approvalObject = requireObjectNode(approvalNode, context);
      rejectUnexpectedFields(
          approvalObject, context, ProtocolPostingNestedFieldSets.approvalFields());
      approvals.add(
          new ApprovalReference(
              new ApprovalId(
                  CliRequestPlaceholderValues.requiredRealText(
                      approvalObject,
                      ProtocolPostEntryFields.Approval.APPROVAL_ID,
                      ScaffoldPlaceholders.APPROVAL_ID,
                      context + ".")),
              new ApprovalType(
                  CliRequestPlaceholderValues.requiredRealText(
                      approvalObject,
                      ProtocolPostEntryFields.Approval.APPROVAL_TYPE,
                      ScaffoldPlaceholders.APPROVAL_TYPE,
                      context + ".")),
              new ActorId(
                  CliRequestPlaceholderValues.requiredRealText(
                      approvalObject,
                      ProtocolPostEntryFields.Approval.APPROVER_ID,
                      ScaffoldPlaceholders.APPROVER_ID,
                      context + ".")),
              parseWireValue(
                  requiredText(approvalObject, ProtocolPostEntryFields.Approval.APPROVER_TYPE),
                  context + "." + ProtocolPostEntryFields.Approval.APPROVER_TYPE,
                  ActorType.wireValues(),
                  ActorType::fromWireValue),
              parseWireValue(
                  requiredText(approvalObject, ProtocolPostEntryFields.Approval.DECISION),
                  context + "." + ProtocolPostEntryFields.Approval.DECISION,
                  ApprovalDecision.wireValues(),
                  ApprovalDecision::fromWireValue),
              CanonicalTemporalText.parseUtcInstant(
                  CliRequestPlaceholderValues.requiredRealText(
                      approvalObject,
                      ProtocolPostEntryFields.Approval.APPROVED_AT,
                      ScaffoldPlaceholders.RECORDED_AT,
                      context + "."),
                  context + "." + ProtocolPostEntryFields.Approval.APPROVED_AT)));
      index++;
    }
    return List.copyOf(approvals);
  }
}
