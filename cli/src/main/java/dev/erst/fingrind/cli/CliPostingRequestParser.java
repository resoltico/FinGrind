package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonRequestSupport.optionalText;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.parseAmount;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.rejectForbiddenField;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.requiredArray;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.requiredObject;
import static dev.erst.fingrind.cli.CliJsonRequestSupport.requiredText;

import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses posting-shaped request payloads for direct CLI commands and plan steps. */
final class CliPostingRequestParser {
  private CliPostingRequestParser() {}

  static PostEntryCommand readPostEntryCommand(ObjectNode rootNode) {
    rejectForbiddenField(rootNode, ProtocolPostEntryFields.TopLevel.CORRECTION);
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSupport.POST_ENTRY_TOP_LEVEL_FIELDS);
    ObjectNode provenanceNode =
        requiredObject(rootNode, ProtocolPostEntryFields.TopLevel.PROVENANCE);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.REASON);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.RECORDED_AT);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL);
    rejectUnexpectedFields(
        provenanceNode,
        ProtocolPostEntryFields.TopLevel.PROVENANCE,
        CliJsonRequestSupport.PROVENANCE_FIELDS);
    return new PostEntryCommand(
        new JournalEntry(
            LocalDate.parse(
                requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE)),
            readLines(requiredArray(rootNode, ProtocolPostEntryFields.TopLevel.LINES))),
        readReversal(rootNode.get(ProtocolPostEntryFields.TopLevel.REVERSAL)),
        new RequestProvenance(
            new ActorId(requiredText(provenanceNode, ProtocolPostEntryFields.Provenance.ACTOR_ID)),
            parseWireValue(
                requiredText(provenanceNode, ProtocolPostEntryFields.Provenance.ACTOR_TYPE),
                ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
                ActorType.wireValues(),
                ActorType::fromWireValue),
            new CommandId(
                requiredText(provenanceNode, ProtocolPostEntryFields.Provenance.COMMAND_ID)),
            new IdempotencyKey(
                requiredText(provenanceNode, ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY)),
            new CausationId(
                requiredText(provenanceNode, ProtocolPostEntryFields.Provenance.CAUSATION_ID)),
            optionalText(provenanceNode, ProtocolPostEntryFields.Provenance.CORRELATION_ID)
                .map(CorrelationId::new)),
        SourceChannel.CLI);
  }

  static DeclareAccountCommand readDeclareAccountCommand(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, CliJsonRequestSupport.DECLARE_ACCOUNT_FIELDS);
    return new DeclareAccountCommand(
        new AccountCode(requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_CODE)),
        new AccountName(requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_NAME)),
        parseWireValue(
            requiredText(rootNode, ProtocolDeclareAccountFields.NORMAL_BALANCE),
            ProtocolDeclareAccountFields.NORMAL_BALANCE,
            NormalBalance.wireValues(),
            NormalBalance::fromWireValue));
  }

  private static List<JournalLine> readLines(JsonNode linesNode) {
    List<JournalLine> lines = new ArrayList<>();
    int index = 0;
    for (JsonNode lineNode : linesNode) {
      ObjectNode lineObject = requireObjectNode(lineNode, "lines[%d]".formatted(index));
      rejectUnexpectedFields(
          lineObject, "lines[%d]".formatted(index), CliJsonRequestSupport.JOURNAL_LINE_FIELDS);
      lines.add(
          new JournalLine(
              new AccountCode(
                  requiredText(lineObject, ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE)),
              parseWireValue(
                  requiredText(lineObject, ProtocolPostEntryFields.JournalLine.SIDE),
                  ProtocolPostEntryFields.JournalLine.SIDE,
                  JournalLine.EntrySide.wireValues(),
                  JournalLine.EntrySide::fromWireValue),
              new Money(
                  new CurrencyCode(
                      requiredText(lineObject, ProtocolPostEntryFields.JournalLine.CURRENCY_CODE)),
                  parseAmount(
                      requiredText(lineObject, ProtocolPostEntryFields.JournalLine.AMOUNT)))));
      index++;
    }
    return lines;
  }

  private static PostingLineage readReversal(JsonNode reversalNode) {
    if (reversalNode == null || reversalNode.isNull()) {
      return PostingLineage.direct();
    }
    ObjectNode reversalObject =
        requireObjectNode(reversalNode, ProtocolPostEntryFields.TopLevel.REVERSAL);
    rejectForbiddenField(reversalObject, ProtocolPostEntryFields.Reversal.KIND);
    rejectUnexpectedFields(
        reversalObject,
        ProtocolPostEntryFields.TopLevel.REVERSAL,
        CliJsonRequestSupport.REVERSAL_FIELDS);
    return PostingLineage.reversal(
        new ReversalReference(
            new PostingId(
                requiredText(reversalObject, ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID))),
        new ReversalReason(requiredText(reversalObject, ProtocolPostEntryFields.Reversal.REASON)));
  }
}
