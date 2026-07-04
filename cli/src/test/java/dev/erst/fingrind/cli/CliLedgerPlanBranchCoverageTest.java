package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** Focused branch coverage for split ledger-plan parser and payload helpers. */
class CliLedgerPlanBranchCoverageTest extends CliRequestReaderTestSupport {
  @Test
  void postingStepParser_readsPreflightAndEveryCommittedPostingKind() {
    LedgerStep.PreflightEntry preflight =
        CliLedgerPlanPostingStepParser.readPreflightStep(
            stepId("preflight"),
            postingStepNode(OperationId.PREFLIGHT_ENTRY, LedgerStepKind.PREFLIGHT_ENTRY));

    assertEquals(LedgerStepKind.PREFLIGHT_ENTRY, preflight.kind());
    assertCommittedPostingKind(OperationId.RECORD_SALE_SETTLED, LedgerStepKind.RECORD_SALE_SETTLED);
    assertCommittedPostingKind(
        OperationId.RECORD_EXPENSE_SETTLED, LedgerStepKind.RECORD_EXPENSE_SETTLED);
    assertCommittedPostingKind(
        OperationId.RECORD_OWNER_CONTRIBUTION, LedgerStepKind.RECORD_OWNER_CONTRIBUTION);
    assertCommittedPostingKind(
        OperationId.RECORD_OWNER_WITHDRAWAL, LedgerStepKind.RECORD_OWNER_WITHDRAWAL);
    assertCommittedPostingKind(
        OperationId.RECORD_OPENING_POSITION, LedgerStepKind.RECORD_OPENING_POSITION);
    assertCommittedPostingKind(OperationId.RECORD_REVERSAL, LedgerStepKind.RECORD_REVERSAL);
    assertCommittedPostingKind(OperationId.POST_ENTRY, LedgerStepKind.POST_ENTRY);
  }

  @Test
  void postingStepParser_coversRejectedAndNoOpGuardBranches() {
    IllegalArgumentException kindFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliLedgerPlanPostingStepParser.readCommittedStep(
                    stepId("preflight"),
                    LedgerStepKind.PREFLIGHT_ENTRY,
                    postingStepNode(OperationId.PREFLIGHT_ENTRY, LedgerStepKind.PREFLIGHT_ENTRY)));

    assertEquals(
        "Ledger step kind preflight-entry does not own one committed posting topic.",
        kindFailure.getMessage());
    assertDoesNotThrow(
        () ->
            CliLedgerPlanPostingStepParser.rejectFlattenedPostingPayload(
                postingStepNode(
                    OperationId.RECORD_SALE_SETTLED, LedgerStepKind.RECORD_SALE_SETTLED),
                LedgerStepKind.RECORD_SALE_SETTLED,
                List.of("effectiveDate")));
    assertDoesNotThrow(
        () ->
            CliLedgerPlanPostingStepParser.rejectFlattenedPostingPayload(
                bareStepNode(LedgerStepKind.ENSURE_BOOK),
                LedgerStepKind.ENSURE_BOOK,
                List.of("effectiveDate")));
    assertDoesNotThrow(
        () ->
            CliLedgerPlanPostingStepParser.rejectFlattenedPostingPayload(
                bareStepNode(LedgerStepKind.RECORD_SALE_SETTLED),
                LedgerStepKind.RECORD_SALE_SETTLED,
                List.of("bogus")));

    IllegalArgumentException flattenedFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliLedgerPlanPostingStepParser.rejectFlattenedPostingPayload(
                    bareStepNode(LedgerStepKind.RECORD_EXPENSE_SETTLED),
                    LedgerStepKind.RECORD_EXPENSE_SETTLED,
                    List.of("effectiveDate", "amount")));

    assertEquals(
        "Fields effectiveDate, amount must be nested under posting for record-expense-settled ledger plan steps.",
        flattenedFailure.getMessage());
  }

  @Test
  void postingRequestParser_acceptsNullTopicAndDataMapperCoversDeclareAccountBranch() {
    ObjectNode requestNode =
        (ObjectNode)
            CliJsonObjectMappers.configuredObjectMapper().readTree(validRequestJson(false));

    PostEntryCommand command = CliPostingRequestParser.readPostEntryCommand(requestNode, null);
    LedgerJournalEntry declareAccountEntry =
        new LedgerJournalEntry.Succeeded(
            stepId("declare"),
            LedgerJournalStep.standard(LedgerStepKind.DECLARE_ACCOUNT),
            Instant.parse("2026-05-15T10:00:00Z"),
            Instant.parse("2026-05-15T10:00:01Z"),
            declareAccountFacts());

    assertEquals(BookkeepingEntryKind.SALE_SETTLED, command.entry().entryKind());
    CliPlanJsonModels.AccountDeclarationStepDataPayload payload =
        assertInstanceOf(
            CliPlanJsonModels.AccountDeclarationStepDataPayload.class,
            CliLedgerStepDataPayloadMapper.ledgerStepDataPayload(declareAccountEntry));
    assertEquals("declared", payload.outcome());
    assertEquals("1110", payload.account().accountCode());
  }

  private static void assertCommittedPostingKind(OperationId operationId, LedgerStepKind kind) {
    LedgerStep.PostEntry step =
        CliLedgerPlanPostingStepParser.readCommittedStep(
            stepId(kind.wireValue()), kind, postingStepNode(operationId, kind));

    assertEquals(kind, step.kind());
  }

  private static ObjectNode postingStepNode(OperationId operationId, LedgerStepKind kind) {
    ObjectNode stepNode = bareStepNode(kind);
    ObjectNode postingNode = postingRequestNode(operationId);
    ObjectNode provenanceNode = (ObjectNode) postingNode.path("provenance");
    provenanceNode.put("actorId", "actor-1");
    provenanceNode.put("commandId", "command-1");
    provenanceNode.put("idempotencyKey", "idem-1");
    provenanceNode.put("causationId", "cause-1");
    stepNode.set("posting", postingNode);
    return stepNode;
  }

  private static ObjectNode postingRequestNode(OperationId operationId) {
    if (operationId == OperationId.PREFLIGHT_ENTRY) {
      return (ObjectNode)
          CliJsonObjectMappers.configuredObjectMapper().readTree(validRequestJson(false));
    }
    String requestJson =
        CliWireJson.jsonText(Objects.requireNonNull(MachineContract.requestTemplate(operationId)))
            .replace(ScaffoldPlaceholders.EFFECTIVE_DATE, "2026-04-07")
            .replace(ScaffoldPlaceholders.RECORDED_AT, "2026-04-07T12:00:00Z")
            .replace(ScaffoldPlaceholders.ACTOR_ID, "actor-1")
            .replace(ScaffoldPlaceholders.COMMAND_ID, "command-1")
            .replace(ScaffoldPlaceholders.IDEMPOTENCY_KEY, "idem-1")
            .replace(ScaffoldPlaceholders.CAUSATION_ID, "cause-1")
            .replace(ScaffoldPlaceholders.SOURCE_DOCUMENT_ID, "document-1")
            .replace(ScaffoldPlaceholders.SOURCE_DOCUMENT_TYPE, "invoice.pdf")
            .replace(ScaffoldPlaceholders.APPROVAL_ID, "approval-1")
            .replace(ScaffoldPlaceholders.APPROVAL_TYPE, "manager-signoff")
            .replace(ScaffoldPlaceholders.APPROVER_ID, "approver-1");
    return (ObjectNode) CliJsonObjectMappers.configuredObjectMapper().readTree(requestJson);
  }

  private static ObjectNode bareStepNode(LedgerStepKind kind) {
    ObjectNode stepNode = CliJsonObjectMappers.configuredObjectMapper().createObjectNode();
    stepNode.put("stepId", "step-1");
    stepNode.put("kind", kind.wireValue());
    return stepNode;
  }

  private static List<LedgerFact> accountFacts() {
    return List.of(
        LedgerFact.text("accountCode", "1110"),
        LedgerFact.text("accountName", "Operating Cash"),
        LedgerFact.text("accountType", "ASSET"),
        LedgerFact.text("accountNodeKind", "POSTABLE"),
        LedgerFact.text("parentAccountCode", "1100"),
        LedgerFact.text("financialPositionLineClassification", "CURRENT_ASSET"),
        LedgerFact.text("normalBalance", "DEBIT"),
        LedgerFact.flag("active", true),
        LedgerFact.text("declaredAt", "2026-05-14T10:00:00Z"));
  }

  private static List<LedgerFact> declareAccountFacts() {
    return java.util.stream.Stream.concat(
            java.util.stream.Stream.of(LedgerFact.text("outcome", "declared")),
            accountFacts().stream())
        .toList();
  }
}
