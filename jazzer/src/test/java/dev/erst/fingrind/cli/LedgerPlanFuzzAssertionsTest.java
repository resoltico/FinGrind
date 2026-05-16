package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.declareOrdinaryAccountStepJson;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers deterministic ledger-plan assertion helpers shared by Jazzer harnesses. */
class LedgerPlanFuzzAssertionsTest {
  @Test
  void executeAndAssert_returns_success_rejected_and_assertionFailed_snapshots() {
    LedgerPlan successPlan =
        CliFuzzFixtures.readLedgerPlan(validLedgerPlanWithQueries().getBytes(UTF_8));
    LedgerPlan rejectedPlan =
        CliFuzzFixtures.readLedgerPlan(rejectedMissingBookListPostingsLedgerPlan().getBytes(UTF_8));
    LedgerPlan assertionFailurePlan =
        CliFuzzFixtures.readLedgerPlan(
            """
            {
              "planId": "assertion-failure",
              "steps": [
                {
                  "stepId": "open",
                  "kind": "open-book",
                  "openBook": %s
                },
                %s,
                %s,
                {
                  "stepId": "post-sale",
                  "kind": "post-entry",
                  "posting": {
                    "postingKind": "STANDARD",
                    "effectiveDate": "2026-04-07",
                    "lines": [
                      {
                        "accountCode": "1000",
                        "side": "DEBIT",
                        "amount": {
                          "currencyCode": "EUR",
                          "minorUnits": "1000"
                        }
                      },
                      {
                        "accountCode": "2000",
                        "side": "CREDIT",
                        "amount": {
                          "currencyCode": "EUR",
                          "minorUnits": "1000"
                        }
                      }
                    ],
                    "provenance": {
                      "actorId": "agent-1",
                      "actorType": "AGENT",
                      "commandId": "command-1",
                      "idempotencyKey": "idem-assertion",
                      "causationId": "cause-1"
                    }
                  }
                },
                {
                  "stepId": "assert-cash",
                  "kind": "assert",
                  "assertion": {
                    "kind": "assert-account-balance",
                    "accountCode": "1000",
                    "netAmount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1100"
                    },
                    "balanceSide": "DEBIT"
                  }
                }
              ]
            }
            """
                .formatted(
                    canonicalOpenBookJson("EUR"),
                    declareOrdinaryAccountStepJson(
                            "declare-cash", "1000", "Cash", AccountType.ASSET)
                        .indent(16)
                        .stripLeading(),
                    declareOrdinaryAccountStepJson(
                            "declare-revenue", "2000", "Revenue", AccountType.REVENUE)
                        .indent(16)
                        .stripLeading())
                .getBytes(UTF_8));

    LedgerPlanFuzzAssertions.ExecutionSnapshot successSnapshot =
        LedgerPlanFuzzAssertions.executeAndAssert(successPlan, "success".getBytes(UTF_8));
    LedgerPlanFuzzAssertions.ExecutionSnapshot rejectedSnapshot =
        LedgerPlanFuzzAssertions.executeAndAssert(rejectedPlan, "rejected".getBytes(UTF_8));
    LedgerPlanFuzzAssertions.ExecutionSnapshot assertionFailedSnapshot =
        LedgerPlanFuzzAssertions.executeAndAssert(
            assertionFailurePlan, "assertion".getBytes(UTF_8));

    assertEquals(LedgerPlanStatus.SUCCEEDED, successSnapshot.executionStatus());
    assertEquals(LedgerPlanStatus.REJECTED, rejectedSnapshot.executionStatus());
    assertEquals(LedgerPlanStatus.ASSERTION_FAILED, assertionFailedSnapshot.executionStatus());
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerPlanFuzzAssertions.ExecutionSnapshot(LedgerPlanStatus.SUCCEEDED, -1, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerPlanFuzzAssertions.ExecutionSnapshot(LedgerPlanStatus.SUCCEEDED, 1, -1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerPlanFuzzAssertions.ExecutionSnapshot(LedgerPlanStatus.SUCCEEDED, 1, 1, -1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerPlanFuzzAssertions.ExecutionSnapshot(LedgerPlanStatus.SUCCEEDED, 1, 0, 1));
  }

  @Test
  void private_fact_assertions_enforce_structured_and_rejected_query_contracts() throws Exception {
    LedgerJournalEntry.Succeeded structuredAccountPage =
        succeededEntry(
            "list-accounts",
            LedgerStepKind.LIST_ACCOUNTS,
            List.of(
                LedgerFact.count("count", 1),
                LedgerFact.count("pageLimit", 2),
                LedgerFact.flag("hasMore", true),
                LedgerFact.text("nextCursor", "cursor-1"),
                LedgerFact.group("account", List.of(LedgerFact.text("accountCode", "1000")))));
    LedgerPlanFuzzAssertions.assertStructuredListQueryFacts(structuredAccountPage);

    IllegalStateException missingCursor =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.assertStructuredListQueryFacts(
                    succeededEntry(
                        "list-accounts",
                        LedgerStepKind.LIST_ACCOUNTS,
                        List.of(
                            LedgerFact.count("count", 1),
                            LedgerFact.count("pageLimit", 2),
                            LedgerFact.flag("hasMore", true),
                            LedgerFact.group(
                                "account", List.of(LedgerFact.text("accountCode", "1000")))))));
    assertTrue(String.valueOf(missingCursor.getMessage()).contains("omitted nextCursor"));

    IllegalStateException wrongType =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.requiredCountFact(
                    List.of(LedgerFact.flag("count", true)), "count"));
    assertTrue(String.valueOf(wrongType.getMessage()).contains("wrong fact kind"));

    LedgerJournalEntry.Rejected rejectedEntry =
        rejectedEntry("list-postings", LedgerStepKind.LIST_POSTINGS, List.of());
    LedgerPlanFuzzAssertions.assertRejectedListQueryFacts(rejectedEntry);
    IllegalStateException successOnlyFact =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.assertRejectedListQueryFacts(
                    rejectedEntry(
                        "list-postings",
                        LedgerStepKind.LIST_POSTINGS,
                        List.of(LedgerFact.count("count", 1)))));
    assertTrue(String.valueOf(successOnlyFact.getMessage()).contains("success-only fact"));
  }

  @Test
  void assertPlanResult_rejects_mismatched_metadata_and_incomplete_success() throws Exception {
    LedgerPlan plan = CliFuzzFixtures.readLedgerPlan(basicValidLedgerPlan().getBytes(UTF_8));
    LedgerJournalEntry.Succeeded openBookStep =
        succeededEntry(
            "open", LedgerStepKind.OPEN_BOOK, List.of(LedgerFact.text("status", "opened")));
    LedgerPlanResult.Succeeded truncatedSuccess =
        new LedgerPlanResult.Succeeded(
            new LedgerPlanId(plan.planId().value()),
            new LedgerExecutionJournal(
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:01Z"),
                List.of(openBookStep)));

    IllegalStateException mismatchedPlanId =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.assertPlanResult(
                    new LedgerPlan(new LedgerPlanId("other-plan"), plan.steps()),
                    truncatedSuccess));
    assertTrue(String.valueOf(mismatchedPlanId.getMessage()).contains("changed the plan id"));

    IllegalStateException incompleteSuccess =
        assertThrows(
            IllegalStateException.class,
            () -> LedgerPlanFuzzAssertions.assertPlanResult(plan, truncatedSuccess));
    assertTrue(String.valueOf(incompleteSuccess.getMessage()).contains("omitted journal steps"));
  }

  @Test
  void assertPlanResult_rejects_journal_overflow_step_id_drift_and_step_kind_drift()
      throws Exception {
    LedgerPlan plan = CliFuzzFixtures.readLedgerPlan(basicValidLedgerPlan().getBytes(UTF_8));
    LedgerJournalEntry.Succeeded openBookStep =
        succeededEntry(
            "open", LedgerStepKind.OPEN_BOOK, List.of(LedgerFact.text("status", "opened")));

    LedgerPlanResult.Succeeded journalOverflow =
        new LedgerPlanResult.Succeeded(
            plan.planId(),
            new LedgerExecutionJournal(
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:01Z"),
                java.util.stream.Stream
                    .<dev.erst.fingrind.contract.workflow.LedgerJournalEntry>concat(
                        plan.steps().stream()
                            .map(
                                step ->
                                    succeededEntry(step.stepId().value(), step.kind(), List.of())),
                        java.util.stream.Stream.of(openBookStep))
                    .toList()));
    IllegalStateException overflow =
        assertThrows(
            IllegalStateException.class,
            () -> LedgerPlanFuzzAssertions.assertPlanResult(plan, journalOverflow));
    assertTrue(String.valueOf(overflow.getMessage()).contains("exceeded the declared step count"));

    LedgerPlanResult.Succeeded stepIdMismatch =
        new LedgerPlanResult.Succeeded(
            plan.planId(),
            new LedgerExecutionJournal(
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:01Z"),
                List.of(
                    openBookStep,
                    succeededEntry("drifted-step", plan.steps().get(1).kind(), List.of()))));
    IllegalStateException stepIdDrift =
        assertThrows(
            IllegalStateException.class,
            () -> LedgerPlanFuzzAssertions.assertPlanResult(plan, stepIdMismatch));
    assertTrue(String.valueOf(stepIdDrift.getMessage()).contains("changed step order or identity"));

    LedgerPlanResult.Succeeded stepKindMismatch =
        new LedgerPlanResult.Succeeded(
            plan.planId(),
            new LedgerExecutionJournal(
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:01Z"),
                List.of(
                    openBookStep,
                    succeededEntry(
                        plan.steps().get(1).stepId().value(),
                        LedgerStepKind.OPEN_BOOK,
                        List.of()))));
    IllegalStateException stepKindDrift =
        assertThrows(
            IllegalStateException.class,
            () -> LedgerPlanFuzzAssertions.assertPlanResult(plan, stepKindMismatch));
    assertTrue(
        String.valueOf(stepKindDrift.getMessage()).contains("changed the declared step kind"));
  }

  @Test
  void assertPlanResult_accepts_terminal_boundary_and_rejects_nonterminal_boundary()
      throws Exception {
    LedgerPlan plan = CliFuzzFixtures.readLedgerPlan(basicValidLedgerPlan().getBytes(UTF_8));
    List<LedgerJournalEntry> succeededSteps =
        plan.steps().stream()
            .map(step -> succeededEntry(step.stepId().value(), step.kind(), List.of()))
            .map(LedgerJournalEntry.class::cast)
            .toList();
    LedgerPlanResult.Rejected terminalBoundaryResult =
        new LedgerPlanResult.Rejected(
            plan.planId(),
            new LedgerExecutionJournal(
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:01Z"),
                java.util.stream.Stream.concat(
                        succeededSteps.stream(),
                        java.util.stream.Stream.of(
                            boundaryRejectedEntry(
                                "@plan-boundary:commit", LedgerBoundaryPhase.COMMIT)))
                    .toList()));

    LedgerPlanFuzzAssertions.ExecutionSnapshot snapshot =
        LedgerPlanFuzzAssertions.assertPlanResult(plan, terminalBoundaryResult);
    assertEquals(LedgerPlanStatus.REJECTED, snapshot.executionStatus());
    assertEquals(plan.steps().size() + 1, snapshot.journalStepCount());

    LedgerPlanResult.Rejected nonterminalBoundaryResult =
        new LedgerPlanResult.Rejected(
            plan.planId(),
            new LedgerExecutionJournal(
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:01Z"),
                List.of(
                    boundarySucceededEntry("@plan-boundary:begin", LedgerBoundaryPhase.BEGIN),
                    rejectedEntry("open", LedgerStepKind.OPEN_BOOK, List.of()))));
    IllegalStateException nonterminalBoundary =
        assertThrows(
            IllegalStateException.class,
            () -> LedgerPlanFuzzAssertions.assertPlanResult(plan, nonterminalBoundaryResult));
    assertTrue(String.valueOf(nonterminalBoundary.getMessage()).contains("must be terminal"));
  }

  @Test
  void assertPlanResult_accepts_structured_account_and_posting_list_query_steps() throws Exception {
    LedgerPlan plan = CliFuzzFixtures.readLedgerPlan(validLedgerPlanWithQueries().getBytes(UTF_8));
    LedgerPlanResult.Succeeded structuredQuerySuccess =
        new LedgerPlanResult.Succeeded(
            plan.planId(),
            new LedgerExecutionJournal(
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:01Z"),
                List.of(
                    succeededEntry("open", LedgerStepKind.OPEN_BOOK, List.of()),
                    succeededEntry("declare-cash", LedgerStepKind.DECLARE_ACCOUNT, List.of()),
                    succeededEntry("declare-revenue", LedgerStepKind.DECLARE_ACCOUNT, List.of()),
                    succeededEntry("post-sale", LedgerStepKind.POST_ENTRY, List.of()),
                    succeededEntry(
                        "page-accounts",
                        LedgerStepKind.LIST_ACCOUNTS,
                        List.of(
                            LedgerFact.count("count", 1),
                            LedgerFact.count("pageLimit", 1),
                            LedgerFact.flag("hasMore", false),
                            LedgerFact.group(
                                "account", List.of(LedgerFact.text("accountCode", "1000"))))),
                    succeededEntry(
                        "page-postings",
                        LedgerStepKind.LIST_POSTINGS,
                        List.of(
                            LedgerFact.count("count", 1),
                            LedgerFact.count("pageLimit", 1),
                            LedgerFact.flag("hasMore", false),
                            LedgerFact.group(
                                "posting", List.of(LedgerFact.text("postingId", "posting-1"))))))));

    LedgerPlanFuzzAssertions.ExecutionSnapshot snapshot =
        LedgerPlanFuzzAssertions.assertPlanResult(plan, structuredQuerySuccess);

    assertEquals(LedgerPlanStatus.SUCCEEDED, snapshot.executionStatus());
    assertEquals(plan.steps().size(), snapshot.journalStepCount());
    assertEquals(2, snapshot.listQueryStepCount());
    assertEquals(2, snapshot.structuredListQueryStepCount());
  }

  @Test
  void assertPlanResult_accepts_every_declared_non_boundary_journal_kind() throws Exception {
    LedgerPlan plan = CliFuzzFixtures.readLedgerPlan(fullSpectrumLedgerPlan().getBytes(UTF_8));
    LedgerPlanResult.Succeeded fullSpectrumSuccess =
        new LedgerPlanResult.Succeeded(
            plan.planId(),
            new LedgerExecutionJournal(
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:01Z"),
                List.of(
                    succeededEntry("open", LedgerStepKind.OPEN_BOOK, List.of()),
                    succeededEntry("declare-cash", LedgerStepKind.DECLARE_ACCOUNT, List.of()),
                    succeededEntry("declare-revenue", LedgerStepKind.DECLARE_ACCOUNT, List.of()),
                    succeededEntry("preflight-sale", LedgerStepKind.PREFLIGHT_ENTRY, List.of()),
                    succeededEntry("post-sale", LedgerStepKind.POST_ENTRY, List.of()),
                    succeededEntry("inspect-book", LedgerStepKind.INSPECT_BOOK, List.of()),
                    succeededEntry(
                        "page-accounts",
                        LedgerStepKind.LIST_ACCOUNTS,
                        List.of(
                            LedgerFact.count("count", 1),
                            LedgerFact.count("pageLimit", 2),
                            LedgerFact.flag("hasMore", false),
                            LedgerFact.group(
                                "account", List.of(LedgerFact.text("accountCode", "1000"))))),
                    succeededEntry("get-posting", LedgerStepKind.GET_POSTING, List.of()),
                    succeededEntry(
                        "page-postings",
                        LedgerStepKind.LIST_POSTINGS,
                        List.of(
                            LedgerFact.count("count", 1),
                            LedgerFact.count("pageLimit", 2),
                            LedgerFact.flag("hasMore", false),
                            LedgerFact.group(
                                "posting", List.of(LedgerFact.text("postingId", "posting-1"))))),
                    succeededEntry("account-balance", LedgerStepKind.ACCOUNT_BALANCE, List.of()),
                    succeededAssertionEntry(
                        "assert-balance", LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS))));

    LedgerPlanFuzzAssertions.ExecutionSnapshot snapshot =
        LedgerPlanFuzzAssertions.assertPlanResult(plan, fullSpectrumSuccess);

    assertEquals(LedgerPlanStatus.SUCCEEDED, snapshot.executionStatus());
    assertEquals(plan.steps().size(), snapshot.journalStepCount());
    assertEquals(2, snapshot.listQueryStepCount());
    assertEquals(2, snapshot.structuredListQueryStepCount());
  }

  @Test
  void assertPlanResult_and_fact_helpers_reject_invalid_query_shapes() throws Exception {
    LedgerJournalEntry.Succeeded structuredPostingPage =
        succeededEntry(
            "list-postings",
            LedgerStepKind.LIST_POSTINGS,
            List.of(
                LedgerFact.count("count", 2),
                LedgerFact.count("pageLimit", 1),
                LedgerFact.flag("hasMore", false),
                LedgerFact.group("posting", List.of(LedgerFact.text("postingId", "posting-1")))));
    IllegalStateException invalidCount =
        assertThrows(
            IllegalStateException.class,
            () -> LedgerPlanFuzzAssertions.assertStructuredListQueryFacts(structuredPostingPage));
    assertTrue(String.valueOf(invalidCount.getMessage()).contains("invalid count"));

    IllegalStateException negativeCount =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.assertStructuredListQueryFacts(
                    succeededEntry(
                        "list-postings",
                        LedgerStepKind.LIST_POSTINGS,
                        List.of(
                            LedgerFact.count("count", -1),
                            LedgerFact.count("pageLimit", 1),
                            LedgerFact.flag("hasMore", false)))));
    assertTrue(String.valueOf(negativeCount.getMessage()).contains("invalid count"));

    IllegalStateException nonPositiveLimit =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.assertStructuredListQueryFacts(
                    succeededEntry(
                        "list-postings",
                        LedgerStepKind.LIST_POSTINGS,
                        List.of(
                            LedgerFact.count("count", 0),
                            LedgerFact.count("pageLimit", 0),
                            LedgerFact.flag("hasMore", false)))));
    assertTrue(String.valueOf(nonPositiveLimit.getMessage()).contains("non-positive limit"));

    IllegalStateException wrongGroupCount =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.assertStructuredListQueryFacts(
                    succeededEntry(
                        "list-postings",
                        LedgerStepKind.LIST_POSTINGS,
                        List.of(
                            LedgerFact.count("count", 1),
                            LedgerFact.count("pageLimit", 2),
                            LedgerFact.flag("hasMore", false)))));
    assertTrue(String.valueOf(wrongGroupCount.getMessage()).contains("lost row groups"));

    IllegalStateException terminalCursor =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.assertStructuredListQueryFacts(
                    succeededEntry(
                        "list-accounts",
                        LedgerStepKind.LIST_ACCOUNTS,
                        List.of(
                            LedgerFact.count("count", 1),
                            LedgerFact.count("pageLimit", 2),
                            LedgerFact.flag("hasMore", false),
                            LedgerFact.text("nextCursor", "cursor-1"),
                            LedgerFact.group(
                                "account", List.of(LedgerFact.text("accountCode", "1000")))))));
    assertTrue(String.valueOf(terminalCursor.getMessage()).contains("retained nextCursor"));

    IllegalStateException duplicateCount =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.requiredCountFact(
                    List.of(LedgerFact.count("count", 1), LedgerFact.count("count", 2)), "count"));
    assertTrue(String.valueOf(duplicateCount.getMessage()).contains("exactly one count fact"));

    IllegalStateException duplicateFlag =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.requiredFlagFact(
                    List.of(LedgerFact.flag("hasMore", true), LedgerFact.flag("hasMore", false)),
                    "hasMore"));
    assertTrue(String.valueOf(duplicateFlag.getMessage()).contains("exactly one flag fact"));

    IllegalStateException rejectedAccountsSuccessOnlyFact =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanFuzzAssertions.assertRejectedListQueryFacts(
                    rejectedEntry(
                        "list-accounts",
                        LedgerStepKind.LIST_ACCOUNTS,
                        List.of(
                            LedgerFact.group(
                                "account", List.of(LedgerFact.text("accountCode", "1000")))))));
    assertTrue(String.valueOf(rejectedAccountsSuccessOnlyFact.getMessage()).contains("account"));

    IllegalArgumentException wrongListQueryKind =
        assertThrows(
            IllegalArgumentException.class,
            () -> LedgerPlanFuzzAssertions.expectedListQueryGroupName(LedgerJournalKind.OPEN_BOOK));
    assertTrue(String.valueOf(wrongListQueryKind.getMessage()).contains("list-query journal kind"));
  }

  private static LedgerJournalEntry.Succeeded succeededEntry(
      String stepId, LedgerStepKind stepKind, List<LedgerFact> facts) {
    return new LedgerJournalEntry.Succeeded(
        new LedgerStepId(stepId),
        LedgerJournalStep.standard(stepKind),
        Instant.parse("2026-04-07T12:00:00Z"),
        Instant.parse("2026-04-07T12:00:01Z"),
        facts);
  }

  private static LedgerJournalEntry.Rejected rejectedEntry(
      String stepId, LedgerStepKind stepKind, List<LedgerFact> facts) {
    return new LedgerJournalEntry.Rejected(
        new LedgerStepId(stepId),
        LedgerJournalStep.standard(stepKind),
        Instant.parse("2026-04-07T12:00:00Z"),
        Instant.parse("2026-04-07T12:00:01Z"),
        facts,
        new LedgerStepFailure("rejected", "expected rejection", List.of()));
  }

  private static LedgerJournalEntry.Succeeded succeededAssertionEntry(
      String stepId, LedgerAssertionKind assertionKind) {
    return new LedgerJournalEntry.Succeeded(
        new LedgerStepId(stepId),
        LedgerJournalStep.assertion(assertionKind),
        Instant.parse("2026-04-07T12:00:00Z"),
        Instant.parse("2026-04-07T12:00:01Z"),
        List.of());
  }

  private static LedgerJournalEntry.Succeeded boundarySucceededEntry(
      String stepId, LedgerBoundaryPhase phase) {
    return new LedgerJournalEntry.Succeeded(
        new LedgerStepId(stepId),
        LedgerJournalStep.boundary(phase),
        Instant.parse("2026-04-07T12:00:00Z"),
        Instant.parse("2026-04-07T12:00:01Z"),
        List.of());
  }

  private static LedgerJournalEntry.Rejected boundaryRejectedEntry(
      String stepId, LedgerBoundaryPhase phase) {
    return new LedgerJournalEntry.Rejected(
        new LedgerStepId(stepId),
        LedgerJournalStep.boundary(phase),
        Instant.parse("2026-04-07T12:00:00Z"),
        Instant.parse("2026-04-07T12:00:01Z"),
        List.of(),
        new LedgerStepFailure("boundary-rejected", "expected boundary rejection", List.of()));
  }

  private static String basicValidLedgerPlan() {
    return """
        {
          "planId": "plan-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book",
              "openBook": %s
            },
            %s
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading());
  }

  private static String validLedgerPlanWithQueries() {
    return """
        {
          "planId": "plan-query-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book",
              "openBook": %s
            },
            %s,
            %s,
            {
              "stepId": "post-sale",
              "kind": "post-entry",
              "posting": {
                "postingKind": "STANDARD",
                "effectiveDate": "2026-04-07",
                "lines": [
                  {
                    "accountCode": "1000",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  },
                  {
                    "accountCode": "2000",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  }
                ],
                "provenance": {
                  "actorId": "agent-1",
                  "actorType": "AGENT",
                  "commandId": "command-query-1",
                  "idempotencyKey": "idem-query-1",
                  "causationId": "cause-query-1"
                }
              }
            },
            {
              "stepId": "page-accounts",
              "kind": "list-accounts",
              "query": {
                "limit": 1
              }
            },
            {
              "stepId": "page-postings",
              "kind": "list-postings",
              "query": {
                "limit": 1
              }
            }
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-revenue", "2000", "Revenue", AccountType.REVENUE)
                .indent(12)
                .stripLeading());
  }

  private static String fullSpectrumLedgerPlan() {
    return """
        {
          "planId": "plan-spectrum-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book",
              "openBook": %s
            },
            %s,
            %s,
            {
              "stepId": "preflight-sale",
              "kind": "preflight-entry",
              "posting": {
                "postingKind": "STANDARD",
                "effectiveDate": "2026-04-07",
                "lines": [
                  {
                    "accountCode": "1000",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  },
                  {
                    "accountCode": "2000",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  }
                ],
                "provenance": {
                  "actorId": "agent-1",
                  "actorType": "AGENT",
                  "commandId": "command-spectrum-1",
                  "idempotencyKey": "idem-spectrum-1",
                  "causationId": "cause-spectrum-1"
                }
              }
            },
            {
              "stepId": "post-sale",
              "kind": "post-entry",
              "posting": {
                "postingKind": "STANDARD",
                "effectiveDate": "2026-04-07",
                "lines": [
                  {
                    "accountCode": "1000",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  },
                  {
                    "accountCode": "2000",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  }
                ],
                "provenance": {
                  "actorId": "agent-1",
                  "actorType": "AGENT",
                  "commandId": "command-spectrum-2",
                  "idempotencyKey": "idem-spectrum-2",
                  "causationId": "cause-spectrum-2"
                }
              }
            },
            {
              "stepId": "inspect-book",
              "kind": "inspect-book"
            },
            {
              "stepId": "page-accounts",
              "kind": "list-accounts",
              "query": {
                "limit": 1
              }
            },
            {
              "stepId": "get-posting",
              "kind": "get-posting",
              "postingId": "posting-1"
            },
            {
              "stepId": "page-postings",
              "kind": "list-postings",
              "query": {
                "limit": 1
              }
            },
            {
              "stepId": "account-balance",
              "kind": "account-balance",
              "query": {
                "accountCode": "1000",
                "effectiveDateFrom": "2026-04-01",
                "effectiveDateTo": "2026-04-30"
              }
            },
            {
              "stepId": "assert-balance",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-balance",
                "accountCode": "1000",
                "effectiveDateFrom": "2026-04-01",
                "effectiveDateTo": "2026-04-30",
                "netAmount": {
                  "currencyCode": "EUR",
                  "minorUnits": "1000"
                },
                "balanceSide": "DEBIT"
              }
            }
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-revenue", "2000", "Revenue", AccountType.REVENUE)
                .indent(12)
                .stripLeading());
  }

  private static String rejectedMissingBookListPostingsLedgerPlan() {
    return """
        {
          "planId": "play-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "list-postings"
            }
          ]
        }
        """;
  }

  private static String canonicalOpenBookJson(String functionalCurrency) {
    return """
        {
          "entityName": "Acme Studio",
          "entityForm": "COMPANY",
          "functionalCurrency": "%s",
          "fiscalYearStart": "01-01",
          "accountingBasis": "ACCRUAL"
        }
        """
        .formatted(functionalCurrency)
        .indent(16)
        .stripLeading();
  }
}
