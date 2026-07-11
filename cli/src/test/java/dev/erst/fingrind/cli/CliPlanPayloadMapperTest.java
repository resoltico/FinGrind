package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.cli.json.CliPlanLedgerFactJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused coverage for typed ledger-plan payload mapping branches. */
class CliPlanPayloadMapperTest extends CliResponseWriterTestSupport {
  @Test
  void ledgerPlanPayload_mapsTypedWorkflowDataAndFailureFactKinds() {
    Instant startedAt = Instant.parse("2026-05-15T10:00:00Z");
    Instant finishedAt = Instant.parse("2026-05-15T10:00:01Z");
    CliPlanJsonModels.LedgerPlanPayload payload =
        CliPlanPayloadMapper.ledgerPlanPayload(
            new LedgerPlanResult.Succeeded(
                planId("plan-1"),
                new LedgerExecutionJournal(
                    startedAt,
                    finishedAt,
                    List.of(
                        new LedgerJournalEntry.Succeeded(
                            stepId("preflight"),
                            LedgerJournalStep.standard(LedgerStepKind.PREFLIGHT_ENTRY),
                            startedAt,
                            finishedAt,
                            preflightFacts()),
                        new LedgerJournalEntry.Succeeded(
                            stepId("inspect"),
                            LedgerJournalStep.standard(LedgerStepKind.INSPECT_BOOK),
                            startedAt,
                            finishedAt,
                            inspectionFacts()),
                        new LedgerJournalEntry.Succeeded(
                            stepId("get-posting"),
                            LedgerJournalStep.standard(LedgerStepKind.GET_POSTING),
                            startedAt,
                            finishedAt,
                            postingFacts()),
                        new LedgerJournalEntry.Succeeded(
                            stepId("assert-account"),
                            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_DECLARED),
                            startedAt,
                            finishedAt,
                            List.of(LedgerFact.text("accountCode", "1000"))),
                        new LedgerJournalEntry.Succeeded(
                            stepId("assert-posting"),
                            LedgerJournalStep.assertion(LedgerAssertionKind.POSTING_EXISTS),
                            startedAt,
                            finishedAt,
                            List.of(LedgerFact.text("postingId", "posting-1"))),
                        new LedgerJournalEntry.Succeeded(
                            stepId("assert-balance"),
                            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS),
                            startedAt,
                            finishedAt,
                            accountBalanceFacts()),
                        new LedgerJournalEntry.Succeeded(
                            stepId("@plan-boundary:rollback"),
                            LedgerJournalStep.boundary(LedgerBoundaryCheckpoint.ROLLBACK),
                            startedAt,
                            finishedAt,
                            List.of()),
                        new LedgerJournalEntry.Succeeded(
                            stepId("broken-preflight"),
                            LedgerJournalStep.standard(LedgerStepKind.PREFLIGHT_ENTRY),
                            startedAt,
                            finishedAt,
                            List.of(LedgerFact.text("idempotencyKey", "idem-broken"))),
                        new LedgerJournalEntry.Succeeded(
                            stepId("broken-inspect"),
                            LedgerJournalStep.standard(LedgerStepKind.INSPECT_BOOK),
                            startedAt,
                            finishedAt,
                            List.of(LedgerFact.text("state", "initialized"))),
                        new LedgerJournalEntry.Succeeded(
                            stepId("broken-list-accounts"),
                            LedgerJournalStep.standard(LedgerStepKind.LIST_ACCOUNTS),
                            startedAt,
                            finishedAt,
                            List.of(
                                LedgerFact.count("pageLimit", 50),
                                LedgerFact.flag("hasMore", false))),
                        new LedgerJournalEntry.Succeeded(
                            stepId("broken-balance"),
                            LedgerJournalStep.standard(LedgerStepKind.ACCOUNT_BALANCE),
                            startedAt,
                            finishedAt,
                            List.of(
                                LedgerFact.group("account", accountFacts()),
                                LedgerFact.count("bucketCount", 1),
                                LedgerFact.group(
                                    "balance",
                                    List.of(
                                        LedgerFact.money(
                                            "debitTotal", new MonetaryAmount("EUR", "1000")),
                                        LedgerFact.money(
                                            "creditTotal", new MonetaryAmount("EUR", "0")),
                                        LedgerFact.text("balanceSide", "DEBIT")))))))),
            PlanResultDetail.FULL);

    List<CliPlanJsonModels.LedgerJournalEntryPayload> steps =
        Objects.requireNonNull(payload.journal(), "journal").steps();

    CliPlanJsonModels.PreflightEntryStepDataPayload preflight =
        assertInstanceOf(
            CliPlanJsonModels.PreflightEntryStepDataPayload.class, steps.get(0).data());
    assertEquals("idem-1", preflight.idempotencyKey());
    assertEquals("2026-05-14", preflight.effectiveDate());

    CliPlanJsonModels.BookInspectionStepDataPayload inspection =
        assertInstanceOf(
            CliPlanJsonModels.BookInspectionStepDataPayload.class, steps.get(1).data());
    assertEquals("initialized", inspection.state());
    assertTrue(inspection.initialized());
    assertTrue(inspection.compatibleWithCurrentBinary());

    CliPlanJsonModels.PostingStepDataPayload posting =
        assertInstanceOf(CliPlanJsonModels.PostingStepDataPayload.class, steps.get(2).data());
    assertEquals("posting-1", posting.posting().postingId());
    assertEquals(
        "approval-idem-1", posting.posting().evidence().approvals().getFirst().approvalId());
    assertEquals("APPROVED", posting.posting().evidence().approvals().getFirst().decision());
    assertEquals(
        "prior-posting-1",
        Objects.requireNonNull(posting.posting().reversal(), "reversal").priorPostingId());
    assertEquals(
        "operator reversal",
        Objects.requireNonNull(posting.posting().reversal(), "reversal").reason());

    CliPlanJsonModels.AccountCodeAssertionStepDataPayload accountAssertion =
        assertInstanceOf(
            CliPlanJsonModels.AccountCodeAssertionStepDataPayload.class, steps.get(3).data());
    assertEquals("1000", accountAssertion.accountCode());

    CliPlanJsonModels.PostingIdAssertionStepDataPayload postingAssertion =
        assertInstanceOf(
            CliPlanJsonModels.PostingIdAssertionStepDataPayload.class, steps.get(4).data());
    assertEquals("posting-1", postingAssertion.postingId());

    CliPlanJsonModels.AccountBalanceStepDataPayload balanceAssertion =
        assertInstanceOf(
            CliPlanJsonModels.AccountBalanceStepDataPayload.class, steps.get(5).data());
    assertEquals("1100", balanceAssertion.account().parentAccountCode());
    assertEquals(1, balanceAssertion.bucketCount());
    assertEquals("DEBIT", balanceAssertion.balances().getFirst().balanceSide());

    CliPlanJsonModels.PlanBoundaryStepDataPayload boundary =
        assertInstanceOf(CliPlanJsonModels.PlanBoundaryStepDataPayload.class, steps.get(6).data());
    assertEquals("rollback", boundary.checkpoint());
    assertEquals(LedgerBoundaryCheckpoint.ROLLBACK, steps.get(6).boundaryCheckpoint());

    assertNull(steps.get(7).data());
    assertNull(steps.get(8).data());
    assertNull(steps.get(9).data());
    assertNull(steps.get(10).data());
  }

  @Test
  void ledgerPlanPayload_mapsTypedFailureFactPayloadsForTerminalAssertionFailures() {
    Instant startedAt = Instant.parse("2026-05-15T10:00:00Z");
    Instant finishedAt = Instant.parse("2026-05-15T10:00:01Z");
    CliPlanJsonModels.LedgerPlanPayload payload =
        CliPlanPayloadMapper.ledgerPlanPayload(
            new LedgerPlanResult.AssertionFailed(
                planId("plan-2"),
                new LedgerExecutionJournal(
                    startedAt,
                    finishedAt,
                    List.of(
                        new LedgerJournalEntry.AssertionFailed(
                            stepId("assert-account"),
                            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_DECLARED),
                            startedAt,
                            finishedAt,
                            List.of(LedgerFact.text("accountCode", "1000")),
                            new LedgerStepFailure(
                                "assertion-failed",
                                "Account missing.",
                                List.of(
                                    LedgerFact.flag("checked", true),
                                    LedgerFact.count("attempt", 1),
                                    LedgerFact.group(
                                        "context",
                                        List.of(LedgerFact.text("scope", "chart"))))))))),
            PlanResultDetail.FULL);

    CliPlanJsonModels.LedgerJournalEntryPayload step =
        Objects.requireNonNull(payload.journal(), "journal").steps().getFirst();
    CliPlanJsonModels.AccountCodeAssertionStepDataPayload accountAssertion =
        assertInstanceOf(CliPlanJsonModels.AccountCodeAssertionStepDataPayload.class, step.data());
    assertEquals("1000", accountAssertion.accountCode());
    List<CliPlanLedgerFactJsonModels.LedgerFactPayload> failureFacts =
        Objects.requireNonNull(step.failure(), "failure").details();
    assertInstanceOf(CliPlanLedgerFactJsonModels.FlagLedgerFactPayload.class, failureFacts.get(0));
    assertInstanceOf(CliPlanLedgerFactJsonModels.CountLedgerFactPayload.class, failureFacts.get(1));
    CliPlanLedgerFactJsonModels.GroupLedgerFactPayload failureGroup =
        assertInstanceOf(
            CliPlanLedgerFactJsonModels.GroupLedgerFactPayload.class, failureFacts.get(2));
    assertEquals("context", failureGroup.name());
    assertEquals(1, failureGroup.facts().size());
  }

  @Test
  void ledgerPlanPayload_mapsPostingPageSummariesWithoutReversalFacts() {
    Instant startedAt = Instant.parse("2026-05-15T10:00:00Z");
    Instant finishedAt = Instant.parse("2026-05-15T10:00:01Z");
    CliPlanJsonModels.LedgerPlanPayload payload =
        CliPlanPayloadMapper.ledgerPlanPayload(
            new LedgerPlanResult.Succeeded(
                planId("plan-3"),
                new LedgerExecutionJournal(
                    startedAt,
                    finishedAt,
                    List.of(
                        new LedgerJournalEntry.Succeeded(
                            stepId("list-postings"),
                            LedgerJournalStep.standard(LedgerStepKind.LIST_POSTINGS),
                            startedAt,
                            finishedAt,
                            postingPageFactsWithoutReversal())))),
            PlanResultDetail.FULL);

    CliPlanJsonModels.PostingPageStepDataPayload postingPage =
        assertInstanceOf(
            CliPlanJsonModels.PostingPageStepDataPayload.class,
            Objects.requireNonNull(payload.journal(), "journal").steps().getFirst().data());
    assertEquals(1, postingPage.postings().size());
    assertNull(postingPage.postings().getFirst().reversesPostingId());
    assertEquals(List.of("approval-idem-1"), postingPage.postings().getFirst().approvalIds());
  }

  @Test
  void ledgerPlanPayload_mapsPostingPageSummariesWithReversalFacts() {
    Instant startedAt = Instant.parse("2026-05-15T10:00:00Z");
    Instant finishedAt = Instant.parse("2026-05-15T10:00:01Z");
    CliPlanJsonModels.LedgerPlanPayload payload =
        CliPlanPayloadMapper.ledgerPlanPayload(
            new LedgerPlanResult.Succeeded(
                planId("plan-3b"),
                new LedgerExecutionJournal(
                    startedAt,
                    finishedAt,
                    List.of(
                        new LedgerJournalEntry.Succeeded(
                            stepId("list-postings"),
                            LedgerJournalStep.standard(LedgerStepKind.LIST_POSTINGS),
                            startedAt,
                            finishedAt,
                            postingPageFactsWithReversal())))),
            PlanResultDetail.FULL);

    CliPlanJsonModels.PostingPageStepDataPayload postingPage =
        assertInstanceOf(
            CliPlanJsonModels.PostingPageStepDataPayload.class,
            Objects.requireNonNull(payload.journal(), "journal").steps().getFirst().data());
    assertEquals("prior-posting-1", postingPage.postings().getFirst().reversesPostingId());
  }

  @Test
  void ledgerPlanPayload_mapsPostingFactsWithoutReversalDetails() {
    Instant startedAt = Instant.parse("2026-05-15T10:00:00Z");
    Instant finishedAt = Instant.parse("2026-05-15T10:00:01Z");
    CliPlanJsonModels.LedgerPlanPayload payload =
        CliPlanPayloadMapper.ledgerPlanPayload(
            new LedgerPlanResult.Succeeded(
                planId("plan-3c"),
                new LedgerExecutionJournal(
                    startedAt,
                    finishedAt,
                    List.of(
                        new LedgerJournalEntry.Succeeded(
                            stepId("get-posting"),
                            LedgerJournalStep.standard(LedgerStepKind.GET_POSTING),
                            startedAt,
                            finishedAt,
                            postingFactsWithoutReversal())))),
            PlanResultDetail.FULL);

    CliPlanJsonModels.PostingStepDataPayload posting =
        assertInstanceOf(
            CliPlanJsonModels.PostingStepDataPayload.class,
            Objects.requireNonNull(payload.journal(), "journal").steps().getFirst().data());
    assertNull(posting.posting().reversal());
    assertEquals("direct", posting.posting().reversalState());
  }

  private static List<LedgerFact> preflightFacts() {
    return List.of(
        LedgerFact.text("idempotencyKey", "idem-1"),
        LedgerFact.text("effectiveDate", "2026-05-14"));
  }

  private static List<LedgerFact> inspectionFacts() {
    return List.of(
        LedgerFact.text("state", "initialized"),
        LedgerFact.flag("initialized", true),
        LedgerFact.flag("compatibleWithCurrentBinary", true));
  }

  private static List<LedgerFact> postingFacts() {
    return List.of(
        LedgerFact.text("postingId", "posting-1"),
        LedgerFact.text("postingKind", "STANDARD"),
        LedgerFact.text("postingOriginKind", "REVERSAL"),
        LedgerFact.text("reversalState", "reversal"),
        LedgerFact.text("effectiveDate", "2026-05-14"),
        LedgerFact.text("recordedAt", "2026-05-15T10:00:01Z"),
        LedgerFact.group(
            "provenance",
            List.of(
                LedgerFact.text("actorId", "actor-1"),
                LedgerFact.text("actorType", "AGENT"),
                LedgerFact.text("commandId", "command-1"),
                LedgerFact.text("idempotencyKey", "idem-1"),
                LedgerFact.text("causationId", "cause-1"),
                LedgerFact.text("correlationId", "corr-1"),
                LedgerFact.text("sourceChannel", "CLI"))),
        LedgerFact.group(
            "evidence",
            List.of(
                LedgerFact.group(
                    "sourceDocument",
                    List.of(
                        LedgerFact.text("sourceDocumentId", "document-idem-1"),
                        LedgerFact.text("sourceDocumentType", "cash-receipt"),
                        LedgerFact.text("documentDate", "2026-05-14"))),
                LedgerFact.group(
                    "approval",
                    List.of(
                        LedgerFact.text("approvalId", "approval-idem-1"),
                        LedgerFact.text("approvalType", "manager-signoff"),
                        LedgerFact.text("approverId", "approver-1"),
                        LedgerFact.text("approverType", "PERSON"),
                        LedgerFact.text("decision", "APPROVED"),
                        LedgerFact.text("approvedAt", "2026-05-14T10:05:00Z"))))),
        LedgerFact.group(
            "reversal",
            List.of(
                LedgerFact.text("priorPostingId", "prior-posting-1"),
                LedgerFact.text("reason", "operator reversal"))),
        LedgerFact.group(
            "line",
            List.of(
                LedgerFact.text("accountCode", "1000"),
                LedgerFact.text("side", "DEBIT"),
                LedgerFact.money("amount", new MonetaryAmount("EUR", "1000")))));
  }

  private static List<LedgerFact> postingFactsWithoutReversal() {
    return List.of(
        LedgerFact.text("postingId", "posting-1"),
        LedgerFact.text("postingKind", "STANDARD"),
        LedgerFact.text("postingOriginKind", "REVERSAL"),
        LedgerFact.text("reversalState", "direct"),
        LedgerFact.text("effectiveDate", "2026-05-14"),
        LedgerFact.text("recordedAt", "2026-05-15T10:00:01Z"),
        LedgerFact.group(
            "provenance",
            List.of(
                LedgerFact.text("actorId", "actor-1"),
                LedgerFact.text("actorType", "AGENT"),
                LedgerFact.text("commandId", "command-1"),
                LedgerFact.text("idempotencyKey", "idem-1"),
                LedgerFact.text("causationId", "cause-1"),
                LedgerFact.text("correlationId", "corr-1"),
                LedgerFact.text("sourceChannel", "CLI"))),
        LedgerFact.group(
            "evidence",
            List.of(
                LedgerFact.group(
                    "sourceDocument",
                    List.of(
                        LedgerFact.text("sourceDocumentId", "document-idem-1"),
                        LedgerFact.text("sourceDocumentType", "cash-receipt"),
                        LedgerFact.text("documentDate", "2026-05-14"))),
                LedgerFact.group(
                    "approval",
                    List.of(
                        LedgerFact.text("approvalId", "approval-idem-1"),
                        LedgerFact.text("approvalType", "manager-signoff"),
                        LedgerFact.text("approverId", "approver-1"),
                        LedgerFact.text("approverType", "PERSON"),
                        LedgerFact.text("decision", "APPROVED"),
                        LedgerFact.text("approvedAt", "2026-05-14T10:05:00Z"))))),
        LedgerFact.group(
            "line",
            List.of(
                LedgerFact.text("accountCode", "1000"),
                LedgerFact.text("side", "DEBIT"),
                LedgerFact.money("amount", new MonetaryAmount("EUR", "1000")))));
  }

  private static List<LedgerFact> postingPageFactsWithoutReversal() {
    List<LedgerFact> evidenceFacts =
        List.of(
            LedgerFact.group(
                "sourceDocument",
                List.of(
                    LedgerFact.text("sourceDocumentId", "document-idem-1"),
                    LedgerFact.text("sourceDocumentType", "cash-receipt"),
                    LedgerFact.text("documentDate", "2026-05-14"))),
            LedgerFact.group(
                "approval",
                List.of(
                    LedgerFact.text("approvalId", "approval-idem-1"),
                    LedgerFact.text("approvalType", "manager-signoff"),
                    LedgerFact.text("approverId", "approver-1"),
                    LedgerFact.text("approverType", "PERSON"),
                    LedgerFact.text("decision", "APPROVED"),
                    LedgerFact.text("approvedAt", "2026-05-14T10:05:00Z"))));
    List<LedgerFact> postingFacts =
        List.of(
            LedgerFact.text("postingId", "posting-1"),
            LedgerFact.text("postingKind", "STANDARD"),
            LedgerFact.text("postingOriginKind", "REVERSAL"),
            LedgerFact.text("reversalState", "direct"),
            LedgerFact.text("effectiveDate", "2026-05-14"),
            LedgerFact.text("recordedAt", "2026-05-15T10:00:01Z"),
            LedgerFact.money("debitTotal", new MonetaryAmount("EUR", "1000")),
            LedgerFact.money("creditTotal", new MonetaryAmount("EUR", "1000")),
            LedgerFact.text("accountCode", "1000"),
            LedgerFact.text("accountCode", "2000"),
            LedgerFact.group("evidence", evidenceFacts));
    return List.of(
        LedgerFact.count("count", 1),
        LedgerFact.count("pageLimit", 10),
        LedgerFact.flag("hasMore", false),
        LedgerFact.group("posting", postingFacts));
  }

  private static List<LedgerFact> postingPageFactsWithReversal() {
    List<LedgerFact> evidenceFacts =
        List.of(
            LedgerFact.group(
                "sourceDocument",
                List.of(
                    LedgerFact.text("sourceDocumentId", "document-idem-1"),
                    LedgerFact.text("sourceDocumentType", "cash-receipt"),
                    LedgerFact.text("documentDate", "2026-05-14"))),
            LedgerFact.group(
                "approval",
                List.of(
                    LedgerFact.text("approvalId", "approval-idem-1"),
                    LedgerFact.text("approvalType", "manager-signoff"),
                    LedgerFact.text("approverId", "approver-1"),
                    LedgerFact.text("approverType", "PERSON"),
                    LedgerFact.text("decision", "APPROVED"),
                    LedgerFact.text("approvedAt", "2026-05-14T10:05:00Z"))));
    List<LedgerFact> postingFacts =
        List.of(
            LedgerFact.text("postingId", "posting-1"),
            LedgerFact.text("postingKind", "STANDARD"),
            LedgerFact.text("postingOriginKind", "REVERSAL"),
            LedgerFact.text("reversalState", "reversal"),
            LedgerFact.text("priorPostingId", "prior-posting-1"),
            LedgerFact.text("effectiveDate", "2026-05-14"),
            LedgerFact.text("recordedAt", "2026-05-15T10:00:01Z"),
            LedgerFact.money("debitTotal", new MonetaryAmount("EUR", "1000")),
            LedgerFact.money("creditTotal", new MonetaryAmount("EUR", "1000")),
            LedgerFact.text("accountCode", "1000"),
            LedgerFact.text("accountCode", "2000"),
            LedgerFact.group("evidence", evidenceFacts),
            LedgerFact.group(
                "reversal",
                List.of(
                    LedgerFact.text("priorPostingId", "prior-posting-1"),
                    LedgerFact.text("reason", "operator reversal"))));
    return List.of(
        LedgerFact.count("count", 1),
        LedgerFact.count("pageLimit", 10),
        LedgerFact.flag("hasMore", false),
        LedgerFact.group("posting", postingFacts));
  }

  private static List<LedgerFact> accountBalanceFacts() {
    return List.of(
        LedgerFact.group("account", accountFacts()),
        LedgerFact.count("bucketCount", 1),
        LedgerFact.group(
            "balance",
            List.of(
                LedgerFact.money("debitTotal", new MonetaryAmount("EUR", "1000")),
                LedgerFact.money("creditTotal", new MonetaryAmount("EUR", "0")),
                LedgerFact.money("netAmount", new MonetaryAmount("EUR", "1000")),
                LedgerFact.text("balanceSide", "DEBIT"))));
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
}
