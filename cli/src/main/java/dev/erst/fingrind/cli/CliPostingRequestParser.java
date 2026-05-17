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
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    List<JournalLine> lines =
        readLines(requiredArray(rootNode, ProtocolPostEntryFields.TopLevel.LINES));
    PostingLineage reversal =
        readReversal(nullableField(rootNode, ProtocolPostEntryFields.TopLevel.REVERSAL));
    String actorId =
        requiredRealProvenanceText(
            provenanceNode,
            ProtocolPostEntryFields.Provenance.ACTOR_ID,
            ScaffoldPlaceholders.ACTOR_ID);
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
    return new PostEntryCommand(
        parseWireValue(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.POSTING_KIND),
            ProtocolPostEntryFields.TopLevel.POSTING_KIND,
            PostingKind.wireValues(),
            PostingKind::fromWireValue),
        new JournalEntry(effectiveDate, lines),
        reversal,
        new RequestProvenance(
            new ActorId(actorId),
            parseWireValue(
                requiredText(provenanceNode, ProtocolPostEntryFields.Provenance.ACTOR_TYPE),
                ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
                ActorType.wireValues(),
                ActorType::fromWireValue),
            new CommandId(commandId),
            new IdempotencyKey(idempotencyKey),
            new CausationId(causationId),
            optionalText(provenanceNode, ProtocolPostEntryFields.Provenance.CORRELATION_ID)
                .map(CorrelationId::new)),
        SourceChannel.CLI);
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
