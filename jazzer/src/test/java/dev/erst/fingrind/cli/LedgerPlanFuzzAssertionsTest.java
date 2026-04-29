package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.LedgerExecutionJournal;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerJournalKind;
import dev.erst.fingrind.contract.LedgerJournalStep;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.LedgerStepId;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
                  "kind": "open-book"
                },
                {
                  "stepId": "declare-cash",
                  "kind": "declare-account",
                  "declareAccount": {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "normalBalance": "DEBIT"
                  }
                },
                {
                  "stepId": "declare-revenue",
                  "kind": "declare-account",
                  "declareAccount": {
                    "accountCode": "2000",
                    "accountName": "Revenue",
                    "normalBalance": "CREDIT"
                  }
                },
                {
                  "stepId": "post-sale",
                  "kind": "post-entry",
                  "posting": {
                    "effectiveDate": "2026-04-07",
                    "lines": [
                      {
                        "accountCode": "1000",
                        "side": "DEBIT",
                        "currencyCode": "EUR",
                        "amount": "10.00"
                      },
                      {
                        "accountCode": "2000",
                        "side": "CREDIT",
                        "currencyCode": "EUR",
                        "amount": "10.00"
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
                    "currencyCode": "EUR",
                    "netAmount": "11.00",
                    "balanceSide": "DEBIT"
                  }
                }
              ]
            }
            """
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
    invokePrivateVoid(
        "assertStructuredListQueryFacts", LedgerJournalEntry.class, structuredAccountPage);

    IllegalStateException missingCursor =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokePrivateVoid(
                    "assertStructuredListQueryFacts",
                    LedgerJournalEntry.class,
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
                invokePrivate(
                    "requiredCountFact",
                    new Class<?>[] {List.class, String.class},
                    List.of(LedgerFact.flag("count", true)),
                    "count"));
    assertTrue(String.valueOf(wrongType.getMessage()).contains("wrong fact kind"));

    LedgerJournalEntry.Rejected rejectedEntry =
        rejectedEntry("list-postings", LedgerStepKind.LIST_POSTINGS, List.of());
    invokePrivateVoid("assertRejectedListQueryFacts", LedgerJournalEntry.class, rejectedEntry);
    IllegalStateException successOnlyFact =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokePrivateVoid(
                    "assertRejectedListQueryFacts",
                    LedgerJournalEntry.class,
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
                invokePrivate(
                    "assertPlanResult",
                    new Class<?>[] {
                      LedgerPlan.class, dev.erst.fingrind.contract.LedgerPlanResult.class
                    },
                    new LedgerPlan(new LedgerPlanId("other-plan"), plan.steps()),
                    truncatedSuccess));
    assertTrue(String.valueOf(mismatchedPlanId.getMessage()).contains("changed the plan id"));

    IllegalStateException incompleteSuccess =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokePrivate(
                    "assertPlanResult",
                    new Class<?>[] {
                      LedgerPlan.class, dev.erst.fingrind.contract.LedgerPlanResult.class
                    },
                    plan,
                    truncatedSuccess));
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
                java.util.stream.Stream.<dev.erst.fingrind.contract.LedgerJournalEntry>concat(
                        plan.steps().stream()
                            .map(
                                step ->
                                    succeededEntry(step.stepId().value(), step.kind(), List.of())),
                        java.util.stream.Stream.of(openBookStep))
                    .toList()));
    IllegalStateException overflow =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokePrivate(
                    "assertPlanResult",
                    new Class<?>[] {
                      LedgerPlan.class, dev.erst.fingrind.contract.LedgerPlanResult.class
                    },
                    plan,
                    journalOverflow));
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
            () ->
                invokePrivate(
                    "assertPlanResult",
                    new Class<?>[] {
                      LedgerPlan.class, dev.erst.fingrind.contract.LedgerPlanResult.class
                    },
                    plan,
                    stepIdMismatch));
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
            () ->
                invokePrivate(
                    "assertPlanResult",
                    new Class<?>[] {
                      LedgerPlan.class, dev.erst.fingrind.contract.LedgerPlanResult.class
                    },
                    plan,
                    stepKindMismatch));
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
        (LedgerPlanFuzzAssertions.ExecutionSnapshot)
            invokePrivate(
                "assertPlanResult",
                new Class<?>[] {
                  LedgerPlan.class, dev.erst.fingrind.contract.LedgerPlanResult.class
                },
                plan,
                terminalBoundaryResult);
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
            () ->
                invokePrivate(
                    "assertPlanResult",
                    new Class<?>[] {
                      LedgerPlan.class, dev.erst.fingrind.contract.LedgerPlanResult.class
                    },
                    plan,
                    nonterminalBoundaryResult));
    assertTrue(String.valueOf(nonterminalBoundary.getMessage()).contains("must be terminal"));
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
            () ->
                invokePrivateVoid(
                    "assertStructuredListQueryFacts",
                    LedgerJournalEntry.class,
                    structuredPostingPage));
    assertTrue(String.valueOf(invalidCount.getMessage()).contains("invalid count"));

    IllegalStateException negativeCount =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokePrivateVoid(
                    "assertStructuredListQueryFacts",
                    LedgerJournalEntry.class,
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
                invokePrivateVoid(
                    "assertStructuredListQueryFacts",
                    LedgerJournalEntry.class,
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
                invokePrivateVoid(
                    "assertStructuredListQueryFacts",
                    LedgerJournalEntry.class,
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
                invokePrivateVoid(
                    "assertStructuredListQueryFacts",
                    LedgerJournalEntry.class,
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
                invokePrivate(
                    "requiredCountFact",
                    new Class<?>[] {List.class, String.class},
                    List.of(LedgerFact.count("count", 1), LedgerFact.count("count", 2)),
                    "count"));
    assertTrue(String.valueOf(duplicateCount.getMessage()).contains("exactly one count fact"));

    IllegalStateException duplicateFlag =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokePrivate(
                    "requiredFlagFact",
                    new Class<?>[] {List.class, String.class},
                    List.of(LedgerFact.flag("hasMore", true), LedgerFact.flag("hasMore", false)),
                    "hasMore"));
    assertTrue(String.valueOf(duplicateFlag.getMessage()).contains("exactly one flag fact"));

    IllegalStateException rejectedAccountsSuccessOnlyFact =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokePrivateVoid(
                    "assertRejectedListQueryFacts",
                    LedgerJournalEntry.class,
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
            () ->
                invokePrivate(
                    "expectedListQueryGroupName",
                    new Class<?>[] {LedgerJournalKind.class},
                    LedgerJournalKind.OPEN_BOOK));
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

  private static Object invokePrivate(
      String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
    Method method = LedgerPlanFuzzAssertions.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    try {
      return method.invoke(null, arguments);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }

  private static void invokePrivateVoid(String methodName, Class<?> parameterType, Object argument)
      throws Exception {
    invokePrivate(methodName, new Class<?>[] {parameterType}, argument);
  }

  private static String basicValidLedgerPlan() {
    return """
        {
          "planId": "plan-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book"
            },
            {
              "stepId": "declare-cash",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1000",
                "accountName": "Cash",
                "normalBalance": "DEBIT"
              }
            }
          ]
        }
        """;
  }

  private static String validLedgerPlanWithQueries() {
    return """
        {
          "planId": "plan-query-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book"
            },
            {
              "stepId": "declare-cash",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1000",
                "accountName": "Cash",
                "normalBalance": "DEBIT"
              }
            },
            {
              "stepId": "declare-revenue",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "2000",
                "accountName": "Revenue",
                "normalBalance": "CREDIT"
              }
            },
            {
              "stepId": "post-sale",
              "kind": "post-entry",
              "posting": {
                "effectiveDate": "2026-04-07",
                "lines": [
                  {
                    "accountCode": "1000",
                    "side": "DEBIT",
                    "currencyCode": "EUR",
                    "amount": "10.00"
                  },
                  {
                    "accountCode": "2000",
                    "side": "CREDIT",
                    "currencyCode": "EUR",
                    "amount": "10.00"
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
        """;
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
}
