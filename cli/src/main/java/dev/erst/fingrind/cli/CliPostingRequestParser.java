package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.nullableField;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalText;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.rejectForbiddenField;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredArray;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredObject;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses posting-shaped request payloads for direct CLI commands and plan steps. */
final class CliPostingRequestParser {
  private CliPostingRequestParser() {}

  static PostEntryCommand readPostEntryCommand(ObjectNode rootNode) {
    rejectWrappedTopLevelPayload(
        rootNode,
        ProtocolLedgerPlanFields.Step.POSTING,
        CliJsonRequestSchemas.POST_ENTRY_TOP_LEVEL_FIELDS,
        "Posting request fields must be top-level for direct request files; remove the posting wrapper.");
    rejectForbiddenField(rootNode, ProtocolPostEntryFields.TopLevel.CORRECTION);
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSchemas.POST_ENTRY_TOP_LEVEL_FIELDS);
    ObjectNode provenanceNode =
        requiredObject(rootNode, ProtocolPostEntryFields.TopLevel.PROVENANCE);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.REASON);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.RECORDED_AT);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL);
    rejectUnexpectedFields(
        provenanceNode,
        ProtocolPostEntryFields.TopLevel.PROVENANCE,
        CliJsonRequestSchemas.PROVENANCE_FIELDS);
    LocalDate effectiveDate =
        LocalDate.parse(
            requiredRealText(
                rootNode,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ScaffoldPlaceholders.EFFECTIVE_DATE,
                null));
    PostingKind postingKind =
        parseWireValue(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.POSTING_KIND),
            ProtocolPostEntryFields.TopLevel.POSTING_KIND,
            PostingKind.wireValues(),
            PostingKind::fromWireValue);
    List<JournalLine> lines =
        readLines(requiredArray(rootNode, ProtocolPostEntryFields.TopLevel.LINES));
    PostingLineage reversal =
        readReversal(nullableField(rootNode, ProtocolPostEntryFields.TopLevel.REVERSAL));
    JournalEntry journalEntry = new JournalEntry(effectiveDate, lines);
    String actorId =
        requiredRealProvenanceText(
            provenanceNode,
            ProtocolPostEntryFields.Provenance.ACTOR_ID,
            ScaffoldPlaceholders.ACTOR_ID);
    ActorType actorType =
        parseWireValue(
            requiredText(provenanceNode, ProtocolPostEntryFields.Provenance.ACTOR_TYPE),
            ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
            ActorType.wireValues(),
            ActorType::fromWireValue);
    String commandId =
        requiredRealProvenanceText(
            provenanceNode,
            ProtocolPostEntryFields.Provenance.COMMAND_ID,
            ScaffoldPlaceholders.COMMAND_ID);
    String idempotencyKey =
        requiredRealProvenanceText(
            provenanceNode,
            ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
            ScaffoldPlaceholders.IDEMPOTENCY_KEY);
    String causationId =
        requiredRealProvenanceText(
            provenanceNode,
            ProtocolPostEntryFields.Provenance.CAUSATION_ID,
            ScaffoldPlaceholders.CAUSATION_ID);
    Optional<CorrelationId> correlationId =
        optionalText(provenanceNode, ProtocolPostEntryFields.Provenance.CORRELATION_ID)
            .map(CorrelationId::new);
    ObjectNode evidenceNode = requiredObject(rootNode, ProtocolPostEntryFields.TopLevel.EVIDENCE);
    rejectUnexpectedFields(
        evidenceNode,
        ProtocolPostEntryFields.TopLevel.EVIDENCE,
        CliJsonRequestSchemas.EVIDENCE_FIELDS);
    AccountingEvidence evidence = readEvidence(evidenceNode);
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId(actorId),
            actorType,
            new CommandId(commandId),
            new IdempotencyKey(idempotencyKey),
            new CausationId(causationId),
            correlationId);
    return new PostEntryCommand(
        postingKind, journalEntry, reversal, evidence, requestProvenance, SourceChannel.CLI);
  }

  static DeclareAccountCommand readDeclareAccountCommand(ObjectNode rootNode) {
    rejectWrappedTopLevelPayload(
        rootNode,
        ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT,
        CliJsonRequestSchemas.DECLARE_ACCOUNT_FIELDS,
        "Declare-account request fields must be top-level for direct request files; remove the declareAccount wrapper.");
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSchemas.DECLARE_ACCOUNT_FIELDS);
    return new DeclareAccountCommand(
        new AccountCode(requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_CODE)),
        new AccountName(requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_NAME)),
        parseWireValue(
            requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_TYPE),
            ProtocolDeclareAccountFields.ACCOUNT_TYPE,
            AccountType.wireValues(),
            AccountType::fromWireValue),
        parseWireValue(
            requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_ROLE),
            ProtocolDeclareAccountFields.ACCOUNT_ROLE,
            AccountRole.wireValues(),
            AccountRole::fromWireValue),
        new AccountTaxonomy(
            optionalText(rootNode, ProtocolDeclareAccountFields.PARENT_ACCOUNT_CODE)
                .map(AccountCode::new),
            optionalText(
                    rootNode, ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION)
                .map(
                    value ->
                        parseWireValue(
                            value,
                            ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION,
                            FinancialPositionLineClassification.declaredAccountWireValues(),
                            FinancialPositionLineClassification::fromWireValue)),
            optionalText(rootNode, ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION)
                .map(
                    value ->
                        parseWireValue(
                            value,
                            ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION,
                            ProfitAndLossLineClassification.wireValues(),
                            ProfitAndLossLineClassification::fromWireValue))));
  }

  private static List<JournalLine> readLines(JsonNode linesNode) {
    List<JournalLine> lines = new ArrayList<>();
    int index = 0;
    for (JsonNode lineNode : linesNode) {
      ObjectNode lineObject = requireObjectNode(lineNode, "lines[%d]".formatted(index));
      rejectUnexpectedFields(
          lineObject, "lines[%d]".formatted(index), CliJsonRequestSchemas.JOURNAL_LINE_FIELDS);
      lines.add(
          new JournalLine(
              new AccountCode(
                  requiredText(lineObject, ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE)),
              parseWireValue(
                  requiredText(lineObject, ProtocolPostEntryFields.JournalLine.SIDE),
                  ProtocolPostEntryFields.JournalLine.SIDE,
                  JournalLine.EntrySide.wireValues(),
                  JournalLine.EntrySide::fromWireValue),
              CliJsonMoneyParser.requiredPositiveMoney(
                  lineObject, ProtocolPostEntryFields.JournalLine.AMOUNT)));
      index++;
    }
    return lines;
  }

  private static PostingLineage readReversal(@Nullable JsonNode reversalNode) {
    if (reversalNode == null || reversalNode.isNull()) {
      return PostingLineage.direct();
    }
    ObjectNode reversalObject =
        requireObjectNode(reversalNode, ProtocolPostEntryFields.TopLevel.REVERSAL);
    rejectForbiddenField(reversalObject, ProtocolPostEntryFields.Reversal.KIND);
    rejectUnexpectedFields(
        reversalObject,
        ProtocolPostEntryFields.TopLevel.REVERSAL,
        CliJsonRequestSchemas.REVERSAL_FIELDS);
    return PostingLineage.reversal(
        new ReversalReference(
            new PostingId(
                requiredText(reversalObject, ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID))),
        new ReversalReason(requiredText(reversalObject, ProtocolPostEntryFields.Reversal.REASON)));
  }

  private static AccountingEvidence readEvidence(ObjectNode evidenceNode) {
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
          sourceDocumentObject, context, CliJsonRequestSchemas.SOURCE_DOCUMENT_FIELDS);
      sourceDocuments.add(
          new SourceDocumentReference(
              new SourceDocumentId(
                  requiredRealText(
                      sourceDocumentObject,
                      ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_ID,
                      ScaffoldPlaceholders.SOURCE_DOCUMENT_ID,
                      context + ".")),
              new SourceDocumentType(
                  requiredRealText(
                      sourceDocumentObject,
                      ProtocolPostEntryFields.SourceDocument.SOURCE_DOCUMENT_TYPE,
                      ScaffoldPlaceholders.SOURCE_DOCUMENT_TYPE,
                      context + "."))));
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
      rejectUnexpectedFields(approvalObject, context, CliJsonRequestSchemas.APPROVAL_FIELDS);
      approvals.add(
          new ApprovalReference(
              new ApprovalId(
                  requiredRealText(
                      approvalObject,
                      ProtocolPostEntryFields.Approval.APPROVAL_ID,
                      ScaffoldPlaceholders.APPROVAL_ID,
                      context + ".")),
              new ApprovalType(
                  requiredRealText(
                      approvalObject,
                      ProtocolPostEntryFields.Approval.APPROVAL_TYPE,
                      ScaffoldPlaceholders.APPROVAL_TYPE,
                      context + "."))));
      index++;
    }
    return List.copyOf(approvals);
  }

  private static String requiredRealProvenanceText(
      ObjectNode provenanceNode, String fieldName, String reservedValue) {
    String value = requiredRealText(provenanceNode, fieldName, reservedValue, "provenance.");
    return value;
  }

  private static String requiredRealText(
      ObjectNode objectNode,
      String fieldName,
      String reservedValue,
      @Nullable String contextPrefix) {
    String value = requiredText(objectNode, fieldName);
    if (reservedValue.equals(value)) {
      throw new IllegalArgumentException(
          "Scaffold placeholder must be replaced before submission: "
              + (contextPrefix == null ? "" : contextPrefix)
              + fieldName);
    }
    return value;
  }

  private static void rejectWrappedTopLevelPayload(
      ObjectNode rootNode,
      String wrapperFieldName,
      java.util.Set<String> nestedAcceptedFields,
      String message) {
    @Nullable JsonNode wrappedNode = nullableField(rootNode, wrapperFieldName);
    if (wrappedNode == null || wrappedNode.isNull() || !wrappedNode.isObject()) {
      return;
    }
    ObjectNode wrappedObject = requireObjectNode(wrappedNode, wrapperFieldName);
    List<String> wrappedFields =
        CliJsonFieldAccess.unexpectedFields(wrappedObject, null, nestedAcceptedFields);
    if (!wrappedFields.isEmpty()) {
      return;
    }
    List<String> topLevelAcceptedFields =
        rootNode
            .propertyStream()
            .map(java.util.Map.Entry::getKey)
            .filter(nestedAcceptedFields::contains)
            .toList();
    if (!topLevelAcceptedFields.isEmpty()) {
      return;
    }
    throw new IllegalArgumentException(message);
  }
}
