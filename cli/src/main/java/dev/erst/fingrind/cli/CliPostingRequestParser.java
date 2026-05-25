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

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
import java.time.Instant;
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
    BookkeepingEntry entry = readEntry(rootNode);
    ObjectNode provenanceNode =
        requiredObject(rootNode, ProtocolPostEntryFields.TopLevel.PROVENANCE);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.REASON);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.RECORDED_AT);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL);
    rejectUnexpectedFields(
        provenanceNode,
        ProtocolPostEntryFields.TopLevel.PROVENANCE,
        CliJsonRequestSchemas.PROVENANCE_FIELDS);
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
    return new PostEntryCommand(entry, evidence, requestProvenance, SourceChannel.CLI);
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
            parseWireValue(
                requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_NODE_KIND),
                ProtocolDeclareAccountFields.ACCOUNT_NODE_KIND,
                AccountNodeKind.wireValues(),
                AccountNodeKind::fromWireValue),
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

  private static BookkeepingEntry readEntry(ObjectNode rootNode) {
    BookkeepingEntryKind entryKind =
        parseWireValue(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.ENTRY_KIND),
            ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
            BookkeepingEntryKind.wireValues(),
            BookkeepingEntryKind::fromWireValue);
    return switch (entryKind) {
      case CASH_REVENUE -> readCashRevenueEntry(rootNode);
      case CASH_EXPENSE -> readCashExpenseEntry(rootNode);
      case EQUITY_CONTRIBUTION -> readEquityContributionEntry(rootNode);
      case EQUITY_WITHDRAWAL -> readEquityWithdrawalEntry(rootNode);
      case OPENING_BALANCE_ADJUSTMENT -> readOpeningBalanceAdjustmentEntry(rootNode);
      case CORRECTION_ADJUSTMENT -> readCorrectionAdjustmentEntry(rootNode);
      case REVERSAL_ADJUSTMENT -> readReversalAdjustmentEntry(rootNode);
    };
  }

  private static BookkeepingEntry.CashRevenue readCashRevenueEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSchemas.CASH_REVENUE_FIELDS);
    return new BookkeepingEntry.CashRevenue(
        requiredEffectiveDate(rootNode),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE)),
        requiredPositiveAmount(rootNode));
  }

  private static BookkeepingEntry.CashExpense readCashExpenseEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSchemas.CASH_EXPENSE_FIELDS);
    return new BookkeepingEntry.CashExpense(
        requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        requiredPositiveAmount(rootNode));
  }

  private static BookkeepingEntry.EquityContribution readEquityContributionEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSchemas.EQUITY_CONTRIBUTION_FIELDS);
    return new BookkeepingEntry.EquityContribution(
        requiredEffectiveDate(rootNode),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE)),
        requiredPositiveAmount(rootNode));
  }

  private static BookkeepingEntry.EquityWithdrawal readEquityWithdrawalEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSchemas.EQUITY_WITHDRAWAL_FIELDS);
    return new BookkeepingEntry.EquityWithdrawal(
        requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        requiredPositiveAmount(rootNode));
  }

  private static BookkeepingEntry.OpeningBalanceAdjustment readOpeningBalanceAdjustmentEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSchemas.OPENING_BALANCE_ADJUSTMENT_FIELDS);
    return new BookkeepingEntry.OpeningBalanceAdjustment(readAdministrativeJournalEntry(rootNode));
  }

  private static BookkeepingEntry.CorrectionAdjustment readCorrectionAdjustmentEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSchemas.CORRECTION_ADJUSTMENT_FIELDS);
    return new BookkeepingEntry.CorrectionAdjustment(readAdministrativeJournalEntry(rootNode));
  }

  private static BookkeepingEntry.ReversalAdjustment readReversalAdjustmentEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSchemas.REVERSAL_ADJUSTMENT_FIELDS);
    return new BookkeepingEntry.ReversalAdjustment(
        readAdministrativeJournalEntry(rootNode), readRequiredReversal(rootNode));
  }

  private static JournalEntry readAdministrativeJournalEntry(ObjectNode rootNode) {
    return new JournalEntry(
        requiredEffectiveDate(rootNode),
        readLines(requiredArray(rootNode, ProtocolPostEntryFields.TopLevel.LINES)));
  }

  private static LocalDate requiredEffectiveDate(ObjectNode rootNode) {
    return LocalDate.parse(
        requiredRealText(
            rootNode,
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            ScaffoldPlaceholders.EFFECTIVE_DATE,
            null));
  }

  private static MonetaryAmount requiredPositiveAmount(ObjectNode rootNode) {
    return MonetaryAmount.of(
        CliJsonMoneyParser.requiredPositiveMoney(rootNode, ProtocolPostEntryFields.TopLevel.AMOUNT)
            .money());
  }

  private static PostingLineage.Reversal readRequiredReversal(ObjectNode rootNode) {
    ObjectNode reversalObject = requiredObject(rootNode, ProtocolPostEntryFields.TopLevel.REVERSAL);
    return readReversalObject(reversalObject);
  }

  private static PostingLineage.Reversal readReversalObject(ObjectNode reversalObject) {
    rejectForbiddenField(reversalObject, ProtocolPostEntryFields.Reversal.KIND);
    rejectUnexpectedFields(
        reversalObject,
        ProtocolPostEntryFields.TopLevel.REVERSAL,
        CliJsonRequestSchemas.REVERSAL_FIELDS);
    return new PostingLineage.Reversal(
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
                      context + ".")),
              LocalDate.parse(
                  requiredRealText(
                      sourceDocumentObject,
                      ProtocolPostEntryFields.SourceDocument.DOCUMENT_DATE,
                      ScaffoldPlaceholders.EFFECTIVE_DATE,
                      context + ".")),
              Instant.parse(
                  requiredRealText(
                      sourceDocumentObject,
                      ProtocolPostEntryFields.SourceDocument.CAPTURED_AT,
                      ScaffoldPlaceholders.RECORDED_AT,
                      context + ".")),
              new StorageLocator(
                  requiredRealText(
                      sourceDocumentObject,
                      ProtocolPostEntryFields.SourceDocument.STORAGE_LOCATOR,
                      ScaffoldPlaceholders.STORAGE_LOCATOR,
                      context + ".")),
              new ContentSha256(
                  requiredRealText(
                      sourceDocumentObject,
                      ProtocolPostEntryFields.SourceDocument.CONTENT_SHA256,
                      ScaffoldPlaceholders.CONTENT_SHA256,
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
                      context + ".")),
              new ActorId(
                  requiredRealText(
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
              Instant.parse(
                  requiredRealText(
                      approvalObject,
                      ProtocolPostEntryFields.Approval.APPROVED_AT,
                      ScaffoldPlaceholders.RECORDED_AT,
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
