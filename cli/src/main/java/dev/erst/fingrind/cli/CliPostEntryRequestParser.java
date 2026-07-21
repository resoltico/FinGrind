package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectForbiddenField;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredObject;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingNestedFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestTopics;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Optional;
import tools.jackson.databind.node.ObjectNode;

/** Parses post-entry request payloads into command objects. */
final class CliPostEntryRequestParser {
  private static final String LEGACY_CORRECTION_FIELD = "correction";

  private CliPostEntryRequestParser() {}

  static PostEntryCommand readPostEntryCommand(
      ObjectNode rootNode, @org.jspecify.annotations.Nullable OperationId operationId) {
    CliWrappedRequestShapeGuards.rejectWrappedTopLevelPayload(
        rootNode,
        ProtocolLedgerPlanFields.Step.POSTING,
        ProtocolPostingRequestFieldSets.postEntryTopLevelFields(),
        "Posting request fields must be top-level for direct request files; remove the posting wrapper.");
    rejectForbiddenField(rootNode, LEGACY_CORRECTION_FIELD);
    BookkeepingEntry entry = CliBookkeepingEntryRequestParser.readEntry(rootNode);
    requireAcceptedEntryKind(entry.entryKind(), operationId);
    ObjectNode provenanceNode =
        requiredObject(rootNode, ProtocolBusinessEventFields.Core.PROVENANCE);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.REASON);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.RECORDED_AT);
    rejectForbiddenField(provenanceNode, ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL);
    rejectUnexpectedFields(
        provenanceNode,
        ProtocolBusinessEventFields.Core.PROVENANCE,
        ProtocolPostingNestedFieldSets.provenanceFields());
    String commandId =
        CliRequestPlaceholderValues.requiredRealProvenanceText(
            provenanceNode,
            ProtocolPostEntryFields.Provenance.COMMAND_ID,
            ScaffoldPlaceholders.COMMAND_ID);
    String idempotencyKey =
        CliRequestPlaceholderValues.requiredRealProvenanceText(
            provenanceNode,
            ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
            ScaffoldPlaceholders.IDEMPOTENCY_KEY);
    String causationId =
        CliRequestPlaceholderValues.requiredRealProvenanceText(
            provenanceNode,
            ProtocolPostEntryFields.Provenance.CAUSATION_ID,
            ScaffoldPlaceholders.CAUSATION_ID);
    Optional<CorrelationId> correlationId =
        optionalText(provenanceNode, ProtocolPostEntryFields.Provenance.CORRELATION_ID)
            .map(CorrelationId::new);
    ObjectNode evidenceNode = requiredObject(rootNode, ProtocolBusinessEventFields.Core.EVIDENCE);
    rejectUnexpectedFields(
        evidenceNode,
        ProtocolBusinessEventFields.Core.EVIDENCE,
        ProtocolPostingNestedFieldSets.evidenceFields());
    AccountingEvidence evidence = CliAccountingEvidenceRequestParser.readEvidence(evidenceNode);
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new CommandId(commandId),
            new IdempotencyKey(idempotencyKey),
            new CausationId(causationId),
            correlationId);
    return new PostEntryCommand(entry, evidence, requestProvenance, SourceChannel.CLI);
  }

  private static void requireAcceptedEntryKind(
      BookkeepingEntryKind actualEntryKind,
      @org.jspecify.annotations.Nullable OperationId operationId) {
    if (operationId == null || ProtocolPostingRequestTopics.acceptsAnyEntryKind(operationId)) {
      return;
    }
    BookkeepingEntryKind requiredEntryKind =
        ProtocolPostingRequestTopics.requiredEntryKind(operationId).orElse(null);
    if (requiredEntryKind == null || actualEntryKind == requiredEntryKind) {
      return;
    }
    throw new IllegalArgumentException(
        "Command '%s' requires request field %s to be '%s', but the request carries '%s'."
            .formatted(
                operationId.wireName(),
                ProtocolBusinessEventFields.Core.ENTRY_KIND,
                requiredEntryKind.wireValue(),
                actualEntryKind.wireValue()));
  }
}
