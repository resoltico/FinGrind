package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.cashRevenueRequestJson;
import static dev.erst.fingrind.cli.CliFuzzLedgerPlanFixtureSupport.declareOrdinaryAccountStepJson;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers deterministic ledger-plan assertion helpers shared by Jazzer harnesses. */
class LedgerPlanFuzzAssertionsTest {
  @TempDir Path tempDirectory;

  @Test
  void workspaceCreationAndAdmissionFailures_areReportedAndPreflightDeterminesTheFunctionalCurrency()
      throws Exception {
    LedgerPlan preflightPlan =
        CliFuzzFixtures.readLedgerPlan(fullSpectrumLedgerPlan().getBytes(UTF_8));
    assertEquals(
        CurrencyUnit.of("EUR"), LedgerPlanFuzzAssertions.functionalCurrency(preflightPlan));

    assertThrows(
        IllegalStateException.class,
        () ->
            LedgerPlanFuzzAssertions.executeAndAssert(
                preflightPlan,
                new LedgerPlanFuzzAssertions.LedgerPlanWorkspace() {
                  @Override
                  public Path create() throws IOException {
                    throw new IOException("simulated workspace creation failure");
                  }
                }));

    Path inadmissibleWorkspace = tempDirectory.resolve("inadmissible-workspace");
    Files.writeString(inadmissibleWorkspace, "not a directory");
    assertThrows(
        IllegalStateException.class,
        () ->
            LedgerPlanFuzzAssertions.executeAndAssert(
                preflightPlan,
                new LedgerPlanFuzzAssertions.LedgerPlanWorkspace() {
                  @Override
                  public Path create() {
                    return inadmissibleWorkspace;
                  }
                }));
    assertTrue(Files.isRegularFile(inadmissibleWorkspace));
  }

  @Test
  void journalScanSummary_rejects_negative_and_inverted_counts() throws Exception {
    IllegalArgumentException negativeListCount =
        assertThrows(IllegalArgumentException.class, () -> newJournalScanSummary(-1, 0));
    assertTrue(String.valueOf(negativeListCount.getMessage()).contains("must be non-negative"));

    IllegalArgumentException negativeStructuredCount =
        assertThrows(IllegalArgumentException.class, () -> newJournalScanSummary(0, -1));
    assertTrue(
        String.valueOf(negativeStructuredCount.getMessage()).contains("must be non-negative"));

    IllegalArgumentException invertedCounts =
        assertThrows(IllegalArgumentException.class, () -> newJournalScanSummary(0, 1));
    assertTrue(String.valueOf(invertedCounts.getMessage()).contains("must be non-negative"));
  }

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
                %s,
                %s,
                {
                  "stepId": "post-sale",
                  "kind": "record-sale-settled",
                  "posting": %s
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
                    declareOrdinaryAccountStepJson(
                            "declare-cash", "1000", "Cash", AccountType.ASSET)
                        .indent(16)
                        .stripLeading(),
                    declareOrdinaryAccountStepJson(
                            "declare-revenue", "2000", "Revenue", AccountType.REVENUE)
                        .indent(16)
                        .stripLeading(),
                    cashRevenueRequestJson(
                            new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                                "2026-04-07",
                                "1000",
                                "2000",
                                "EUR",
                                "1000",
                                new CliFuzzHarnessTestSupport.RequestContext(
                                    "document-idem-assertion",
                                    "cash-receipt",
                                    "2026-04-07",
                                    "command-1",
                                    "idem-assertion",
                                    "cause-1",
                                    null)))
                        .indent(20)
                        .stripLeading())
                .getBytes(UTF_8));

    LedgerPlanFuzzAssertions.ExecutionSnapshot successSnapshot =
        LedgerPlanFuzzAssertions.executeAndAssert(successPlan);
    LedgerPlanFuzzAssertions.ExecutionSnapshot rejectedSnapshot =
        LedgerPlanFuzzAssertions.executeAndAssert(rejectedPlan);
    LedgerPlanFuzzAssertions.ExecutionSnapshot assertionFailedSnapshot =
        LedgerPlanFuzzAssertions.executeAndAssert(assertionFailurePlan);

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
    LedgerPlanListQueryAssertions.assertStructuredListQueryFacts(structuredAccountPage);

    IllegalStateException missingCursor =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanListQueryAssertions.assertStructuredListQueryFacts(
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
                LedgerPlanListQueryAssertions.requiredCountFact(
                    List.of(LedgerFact.flag("count", true)), "count"));
    assertTrue(String.valueOf(wrongType.getMessage()).contains("wrong fact kind"));

    LedgerJournalEntry.Rejected rejectedEntry =
        rejectedEntry("list-postings", LedgerStepKind.LIST_POSTINGS, List.of());
    LedgerPlanListQueryAssertions.assertRejectedListQueryFacts(rejectedEntry);
    IllegalStateException successOnlyFact =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanListQueryAssertions.assertRejectedListQueryFacts(
                    rejectedEntry(
                        "list-postings",
                        LedgerStepKind.LIST_POSTINGS,
                        List.of(LedgerFact.count("count", 1)))));
    assertTrue(String.valueOf(successOnlyFact.getMessage()).contains("success-only fact"));
  }

  @Test
  void assertPlanResult_rejects_mismatched_metadata_and_incomplete_success() throws Exception {
    LedgerPlan plan = CliFuzzFixtures.readLedgerPlan(validLedgerPlanWithQueries().getBytes(UTF_8));
    LedgerJournalEntry.Succeeded declareCashStep =
        succeededEntry("declare-cash", LedgerStepKind.DECLARE_ACCOUNT, List.of());
    LedgerPlanResult.Succeeded truncatedSuccess =
        new LedgerPlanResult.Succeeded(
            new LedgerPlanId(plan.planId().value()),
            new LedgerExecutionJournal(
                Instant.parse("2026-04-07T12:00:00Z"),
                Instant.parse("2026-04-07T12:00:01Z"),
                List.of(declareCashStep)),
            LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
            null);

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
    LedgerJournalEntry.Succeeded declareCashStep =
        succeededEntry("declare-cash", LedgerStepKind.DECLARE_ACCOUNT, List.of());

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
                        java.util.stream.Stream.of(declareCashStep))
                    .toList()),
            LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
            null);
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
                List.of(succeededEntry("drifted-step", plan.steps().getFirst().kind(), List.of()))),
            LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
            null);
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
                    succeededEntry(
                        plan.steps().getFirst().stepId().value(),
                        LedgerStepKind.INSPECT_BOOK,
                        List.of()))),
            LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
            null);
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
    LedgerPlan fullPlan =
        CliFuzzFixtures.readLedgerPlan(validLedgerPlanWithQueries().getBytes(UTF_8));
    LedgerPlan plan = new LedgerPlan(fullPlan.planId(), fullPlan.steps().subList(0, 2));
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
                                "@plan-boundary:commit", LedgerBoundaryCheckpoint.COMMIT)))
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
                    boundarySucceededEntry("@plan-boundary:begin", LedgerBoundaryCheckpoint.BEGIN),
                    rejectedEntry("declare-cash", LedgerStepKind.DECLARE_ACCOUNT, List.of()))));
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
                    succeededEntry("declare-cash", LedgerStepKind.DECLARE_ACCOUNT, List.of()),
                    succeededEntry("declare-revenue", LedgerStepKind.DECLARE_ACCOUNT, List.of()),
                    succeededEntry("post-sale", LedgerStepKind.RECORD_SALE_SETTLED, List.of()),
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
                                "posting",
                                List.of(
                                    LedgerFact.text(
                                        "postingId", "018f0000-0000-7000-8000-000000000002"))))))),
            LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
            null);

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
                    succeededEntry("declare-cash", LedgerStepKind.DECLARE_ACCOUNT, List.of()),
                    succeededEntry("declare-revenue", LedgerStepKind.DECLARE_ACCOUNT, List.of()),
                    succeededEntry("preflight-sale", LedgerStepKind.PREFLIGHT_ENTRY, List.of()),
                    succeededEntry("post-sale", LedgerStepKind.RECORD_SALE_SETTLED, List.of()),
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
                                "posting",
                                List.of(
                                    LedgerFact.text(
                                        "postingId", "018f0000-0000-7000-8000-000000000002"))))),
                    succeededEntry("account-balance", LedgerStepKind.ACCOUNT_BALANCE, List.of()),
                    succeededAssertionEntry(
                        "assert-balance", LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS))),
            LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
            null);

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
                LedgerFact.group(
                    "posting",
                    List.of(
                        LedgerFact.text("postingId", "018f0000-0000-7000-8000-000000000002")))));
    IllegalStateException invalidCount =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanListQueryAssertions.assertStructuredListQueryFacts(
                    structuredPostingPage));
    assertTrue(String.valueOf(invalidCount.getMessage()).contains("invalid count"));

    IllegalStateException negativeCount =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanListQueryAssertions.assertStructuredListQueryFacts(
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
                LedgerPlanListQueryAssertions.assertStructuredListQueryFacts(
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
                LedgerPlanListQueryAssertions.assertStructuredListQueryFacts(
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
                LedgerPlanListQueryAssertions.assertStructuredListQueryFacts(
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
                LedgerPlanListQueryAssertions.requiredCountFact(
                    List.of(LedgerFact.count("count", 1), LedgerFact.count("count", 2)), "count"));
    assertTrue(String.valueOf(duplicateCount.getMessage()).contains("exactly one count fact"));

    IllegalStateException duplicateFlag =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanListQueryAssertions.requiredFlagFact(
                    List.of(LedgerFact.flag("hasMore", true), LedgerFact.flag("hasMore", false)),
                    "hasMore"));
    assertTrue(String.valueOf(duplicateFlag.getMessage()).contains("exactly one flag fact"));

    IllegalStateException rejectedAccountsSuccessOnlyFact =
        assertThrows(
            IllegalStateException.class,
            () ->
                LedgerPlanListQueryAssertions.assertRejectedListQueryFacts(
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
                LedgerPlanListQueryAssertions.expectedListQueryGroupName(
                    LedgerStepKind.INSPECT_BOOK));
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
      String stepId, LedgerBoundaryCheckpoint checkpoint) {
    return new LedgerJournalEntry.Succeeded(
        new LedgerStepId(stepId),
        LedgerJournalStep.boundary(checkpoint),
        Instant.parse("2026-04-07T12:00:00Z"),
        Instant.parse("2026-04-07T12:00:01Z"),
        List.of());
  }

  private static LedgerJournalEntry.Rejected boundaryRejectedEntry(
      String stepId, LedgerBoundaryCheckpoint checkpoint) {
    return new LedgerJournalEntry.Rejected(
        new LedgerStepId(stepId),
        LedgerJournalStep.boundary(checkpoint),
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
            %s
          ]
        }
        """
        .formatted(
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading());
  }

  private static String validLedgerPlanWithQueries() {
    return """
        {
          "planId": "plan-query-1",
          "steps": [
            %s,
            %s,
            {
              "stepId": "post-sale",
              "kind": "record-sale-settled",
              "posting": %s
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
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-revenue", "2000", "Revenue", AccountType.REVENUE)
                .indent(12)
                .stripLeading(),
            cashRevenueRequestJson(
                    new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                        "2026-04-07",
                        "1000",
                        "2000",
                        "EUR",
                        "1000",
                        new CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-query-1",
                            "cash-receipt",
                            "2026-04-07",
                            "command-query-1",
                            "idem-query-1",
                            "cause-query-1",
                            null)))
                .indent(16)
                .stripLeading());
  }

  private static String fullSpectrumLedgerPlan() {
    return """
        {
          "planId": "plan-spectrum-1",
          "steps": [
            %s,
            %s,
            {
              "stepId": "preflight-sale",
              "kind": "preflight-entry",
              "posting": %s
            },
            {
              "stepId": "post-sale",
              "kind": "record-sale-settled",
              "posting": %s
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
              "postingId": "018f0000-0000-7000-8000-000000000002"
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
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-revenue", "2000", "Revenue", AccountType.REVENUE)
                .indent(12)
                .stripLeading(),
            cashRevenueRequestJson(
                    new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                        "2026-04-07",
                        "1000",
                        "2000",
                        "EUR",
                        "1000",
                        new CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-spectrum-1",
                            "cash-receipt",
                            "2026-04-07",
                            "command-spectrum-1",
                            "idem-spectrum-1",
                            "cause-spectrum-1",
                            null)))
                .indent(16)
                .stripLeading(),
            cashRevenueRequestJson(
                    new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                        "2026-04-07",
                        "1000",
                        "2000",
                        "EUR",
                        "1000",
                        new CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-spectrum-2",
                            "cash-receipt",
                            "2026-04-07",
                            "command-spectrum-2",
                            "idem-spectrum-2",
                            "cause-spectrum-2",
                            null)))
                .indent(16)
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

  private static Object newJournalScanSummary(
      int listQueryStepCount, int structuredListQueryStepCount)
      throws ReflectiveOperationException {
    Class<?> summaryClass =
        Class.forName("dev.erst.fingrind.cli.LedgerPlanFuzzAssertions$JournalScanSummary");
    Constructor<?> constructor = summaryClass.getDeclaredConstructor(int.class, int.class);
    constructor.setAccessible(true);
    try {
      return constructor.newInstance(listQueryStepCount, structuredListQueryStepCount);
    } catch (InvocationTargetException exception) {
      if (exception.getCause() instanceof IllegalArgumentException illegalArgumentException) {
        throw illegalArgumentException;
      }
      throw exception;
    }
  }
}
