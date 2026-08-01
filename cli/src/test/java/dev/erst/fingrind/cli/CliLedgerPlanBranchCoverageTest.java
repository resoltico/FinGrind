package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliPlanStepDataJsonModels;
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
    assertCommittedPostingKind(OperationId.RECORD_PREPAYMENT, LedgerStepKind.RECORD_PREPAYMENT);
    assertCommittedPostingKind(
        OperationId.RECORD_DEFERRED_REVENUE, LedgerStepKind.RECORD_DEFERRED_REVENUE);
    assertCommittedPostingKind(
        OperationId.RECORD_ACCRUED_EXPENSE, LedgerStepKind.RECORD_ACCRUED_EXPENSE);
    assertCommittedPostingKind(
        OperationId.RECORD_ACCRUAL_CUTOFF_RECOGNITION,
        LedgerStepKind.RECORD_ACCRUAL_CUTOFF_RECOGNITION);
    assertCommittedPostingKind(
        OperationId.RECORD_ACCRUED_EXPENSE_SETTLEMENT,
        LedgerStepKind.RECORD_ACCRUED_EXPENSE_SETTLEMENT);
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
                bareStepNode(LedgerStepKind.INSPECT_BOOK),
                LedgerStepKind.INSPECT_BOOK,
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
  void postingRequestParser_acceptsNullTopicAndDataMapperCoversDeclarationBranches() {
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
    CliPlanStepDataJsonModels.AccountDeclarationStepDataPayload payload =
        assertInstanceOf(
            CliPlanStepDataJsonModels.AccountDeclarationStepDataPayload.class,
            CliLedgerStepDataPayloadMapper.ledgerStepDataPayload(declareAccountEntry));
    assertEquals("declared", payload.outcome());
    assertEquals("1110", payload.account().accountCode());

    LedgerJournalEntry declareTaxRegistrationEntry =
        new LedgerJournalEntry.Succeeded(
            stepId("declare-tax"),
            LedgerJournalStep.standard(LedgerStepKind.DECLARE_TAX_REGISTRATION),
            Instant.parse("2026-05-15T10:00:00Z"),
            Instant.parse("2026-05-15T10:00:01Z"),
            declareTaxRegistrationFacts());

    CliPlanStepDataJsonModels.TaxRegistrationDeclarationStepDataPayload taxPayload =
        assertInstanceOf(
            CliPlanStepDataJsonModels.TaxRegistrationDeclarationStepDataPayload.class,
            CliLedgerStepDataPayloadMapper.ledgerStepDataPayload(declareTaxRegistrationEntry));
    assertEquals("declared", taxPayload.outcome());
    assertEquals("vat-lv", taxPayload.taxRegistration().taxRegistrationId());
    assertEquals("vat-standard-sale", taxPayload.taxRegistration().taxCodes().get(0).taxCode());

    LedgerJournalEntry committedEntry =
        new LedgerJournalEntry.Succeeded(
            stepId("record-owner-contribution"),
            LedgerJournalStep.standard(LedgerStepKind.RECORD_OWNER_CONTRIBUTION),
            Instant.parse("2026-05-15T10:00:02Z"),
            Instant.parse("2026-05-15T10:00:03Z"),
            List.of(
                LedgerFact.text("postingId", "018f0000-0000-7000-8000-000000000002"),
                LedgerFact.text("idempotencyKey", "idem-1"),
                LedgerFact.text("effectiveDate", "2026-05-15"),
                LedgerFact.text("recordedAt", "2026-05-15T10:00:03Z")));
    CliPlanStepDataJsonModels.CommittedEntryStepDataPayload committedPayload =
        assertInstanceOf(
            CliPlanStepDataJsonModels.CommittedEntryStepDataPayload.class,
            CliLedgerStepDataPayloadMapper.ledgerStepDataPayload(committedEntry));
    assertEquals("018f0000-0000-7000-8000-000000000002", committedPayload.postingId());
    assertEquals("2026-05-15T10:00:03Z", committedPayload.recordedAt());

    String taxRegistrationText = CliPlanDetailTextRenderer.renderStepData(taxPayload);
    assertTrue(taxRegistrationText.contains("Tax registration id"));
    assertTrue(taxRegistrationText.contains("Tax codes"));
    assertTrue(taxRegistrationText.contains("VAT Standard Sale"));

    CliPlanStepDataJsonModels.TaxRegistrationDeclarationStepDataPayload unnumberedTaxPayload =
        new CliPlanStepDataJsonModels.TaxRegistrationDeclarationStepDataPayload(
            "declared",
            CliLedgerTaxRegistrationPayloadMapper.taxRegistrationPayload(
                declareTaxRegistrationFacts().stream()
                    .filter(
                        fact ->
                            !(fact instanceof LedgerFact.Text text
                                && "registrationNumber".equals(text.name())))
                    .toList()));
    String unnumberedTaxText = CliPlanDetailTextRenderer.renderStepData(unnumberedTaxPayload);
    assertTrue(unnumberedTaxText.contains("Registration number"));
    assertTrue(unnumberedTaxText.contains("(none)"));
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
    provenanceNode.put("commandId", "018f0000-0000-7000-8000-000000000001");
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
            .replace(ScaffoldPlaceholders.COMMAND_ID, "018f0000-0000-7000-8000-000000000001")
            .replace(ScaffoldPlaceholders.IDEMPOTENCY_KEY, "idem-1")
            .replace(ScaffoldPlaceholders.CAUSATION_ID, "cause-1")
            .replace(ScaffoldPlaceholders.SOURCE_DOCUMENT_ID, "document-1")
            .replace(ScaffoldPlaceholders.SOURCE_DOCUMENT_TYPE, "invoice.pdf")
            .replace(ScaffoldPlaceholders.APPROVAL_ID, "approval-1")
            .replace(ScaffoldPlaceholders.APPROVAL_TYPE, "manager-signoff")
            .replace(ScaffoldPlaceholders.APPROVER_REFERENCE, "approver-1");
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

  private static List<LedgerFact> declareTaxRegistrationFacts() {
    return List.of(
        LedgerFact.text("outcome", "declared"),
        LedgerFact.text("taxRegistrationId", "vat-lv"),
        LedgerFact.text("taxRegistrationName", "Latvia VAT"),
        LedgerFact.text("jurisdiction", "LV"),
        LedgerFact.text("registrationNumber", "LV40001234567"),
        LedgerFact.text("payableAccountCode", "2100"),
        LedgerFact.text("recoverableAccountCode", "1300"),
        LedgerFact.text("obligationFrequency", "MONTHLY"),
        LedgerFact.count("dueDaysAfterPeriodEnd", 20),
        LedgerFact.group(
            "taxCode",
            List.of(
                LedgerFact.text("taxCode", "vat-standard-sale"),
                LedgerFact.text("taxCodeName", "VAT Standard Sale"),
                LedgerFact.count("ratePartsPerMillion", 210000),
                LedgerFact.text("inclusionMode", "EXCLUSIVE"),
                LedgerFact.text("applicationKind", "OUTPUT_SALE"))),
        LedgerFact.text("declaredAt", "2026-05-15T10:00:00Z"));
  }
}
