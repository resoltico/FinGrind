package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for ledger facts, journal entries, and execution journals. */
@NullUnmarked
class LedgerJournalModelTest extends ContractTestSupport {
  @Test
  void ledgerFactsAndJournalValidationCoverTypedAndFailureBranches() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", List.of());
    LedgerJournalStep standardStep = LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY);
    LedgerJournalStep assertionStep =
        LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_DECLARED);
    LedgerJournalStep boundaryStep = LedgerJournalStep.boundary(LedgerBoundaryPhase.COMMIT);

    assertEquals("value", LedgerFact.text("text", "value").value());
    assertTrue(LedgerFact.flag("flag", true).value());
    assertEquals(7, LedgerFact.count("count", 7).value());
    assertEquals(
        List.of(LedgerFact.text("currencyCode", "EUR")),
        LedgerFact.group("balance", List.of(LedgerFact.text("currencyCode", "EUR"))).facts());
    assertEquals(LedgerJournalKind.POST_ENTRY, standardStep.kind());
    assertNull(standardStep.detailKind());
    assertEquals(LedgerJournalKind.ASSERT, assertionStep.kind());
    assertEquals(LedgerAssertionKind.ACCOUNT_DECLARED, assertionStep.detailKind());
    assertEquals(LedgerJournalKind.PLAN_BOUNDARY, boundaryStep.kind());
    assertNull(boundaryStep.detailKind());
    assertEquals(LedgerBoundaryPhase.COMMIT, boundaryStep.boundaryPhase());
    assertThrows(NullPointerException.class, () -> new LedgerFact.Text("null", null));
    assertThrows(IllegalArgumentException.class, () -> LedgerFact.count(" ", 7));
    assertThrows(IllegalArgumentException.class, () -> LedgerFact.group("balance", List.of()));
    assertThrows(
        IllegalArgumentException.class, () -> LedgerJournalStep.standard(LedgerStepKind.ASSERT));
    assertThrows(NullPointerException.class, () -> LedgerJournalStep.assertion(null));
    assertThrows(NullPointerException.class, () -> LedgerJournalStep.boundary(null));

    LedgerJournalEntry detailed =
        new LedgerJournalEntry.Rejected(
            stepId("assert"),
            assertionStep,
            startedAt,
            finishedAt,
            List.of(LedgerFact.flag("active", false)),
            failure);
    LedgerJournalEntry nullableOptionals =
        new LedgerJournalEntry.Succeeded(
            stepId("post"), standardStep, startedAt, finishedAt, List.of());
    LedgerJournalEntry assertionFailed =
        new LedgerJournalEntry.AssertionFailed(
            stepId("assert-balance"),
            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS),
            startedAt,
            finishedAt,
            List.of(LedgerFact.text("currencyCode", "EUR")),
            failure);
    LedgerJournalEntry boundaryFailed =
        new LedgerJournalEntry.Rejected(
            stepId("@plan-boundary:commit"),
            boundaryStep,
            startedAt,
            finishedAt,
            List.of(),
            failure);

    assertEquals(LedgerAssertionKind.ACCOUNT_DECLARED, detailed.detailKind());
    assertEquals(LedgerJournalKind.ASSERT, detailed.kind());
    assertNull(nullableOptionals.detailKind());
    assertEquals(LedgerJournalKind.POST_ENTRY, nullableOptionals.kind());
    assertNull(nullableOptionals.boundaryPhase());
    assertEquals(LedgerJournalKind.PLAN_BOUNDARY, boundaryFailed.kind());
    assertEquals(LedgerBoundaryPhase.COMMIT, boundaryFailed.boundaryPhase());
    assertNull(boundaryFailed.detailKind());
    assertNull(new LedgerStep.OpenBook(stepId("open")).detailKind());
    assertEquals(Optional.of(failure), detailed.optionalFailure());
    assertEquals(Optional.empty(), nullableOptionals.optionalFailure());
    assertEquals(Optional.of(failure), assertionFailed.optionalFailure());
    assertEquals(Optional.of(failure), boundaryFailed.optionalFailure());
    assertEquals(LedgerStepStatus.REJECTED, detailed.status());
    assertEquals(LedgerStepStatus.SUCCEEDED, nullableOptionals.status());
    assertEquals(LedgerStepStatus.ASSERTION_FAILED, assertionFailed.status());
    assertThrows(IllegalStateException.class, nullableOptionals::requiredFailure);
    assertEquals(failure, ((LedgerJournalEntry.Failed) detailed).requiredFailure());
    assertEquals(failure, assertionFailed.requiredFailure());
    assertThrows(
        NullPointerException.class,
        () ->
            new LedgerJournalEntry.Rejected(
                stepId("assert"), null, startedAt, finishedAt, List.of(), failure));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.AssertionFailed(
                stepId("assert"),
                LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
                startedAt,
                finishedAt,
                List.of(),
                failure));
    LedgerJournalEntry succeededAssertion =
        new LedgerJournalEntry.Succeeded(
            stepId("post"),
            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_DECLARED),
            startedAt,
            finishedAt,
            List.of());
    assertEquals(LedgerAssertionKind.ACCOUNT_DECLARED, succeededAssertion.detailKind());
  }

  @Test
  void journalKindsAndBoundaryPhasesPublishStableWireValues() {
    assertEquals(
        List.of(
            "open-book",
            "declare-account",
            "preflight-entry",
            "post-entry",
            "inspect-book",
            "list-accounts",
            "get-posting",
            "list-postings",
            "account-balance",
            "assert",
            "plan-boundary"),
        LedgerJournalKind.wireValues());
    assertEquals(LedgerJournalKind.PLAN_BOUNDARY, LedgerJournalKind.fromWireValue("plan-boundary"));
    assertEquals(
        List.of("begin", "initialization-check", "commit", "rollback"),
        LedgerBoundaryPhase.wireValues());
    assertEquals(
        LedgerBoundaryPhase.INITIALIZATION_CHECK,
        LedgerBoundaryPhase.fromWireValue("initialization-check"));
  }

  @Test
  void journalRecordsRejectBlankIdentifiersAndBackwardsTimes() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", List.of());
    LedgerJournalEntry succeededWithoutFailure =
        new LedgerJournalEntry.Succeeded(
            stepId("post"),
            LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
            startedAt,
            finishedAt,
            List.of());
    LedgerExecutionJournal rejectedJournal =
        new LedgerExecutionJournal(
            startedAt,
            finishedAt,
            List.of(
                new LedgerJournalEntry.Rejected(
                    stepId("post"),
                    LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
                    startedAt,
                    finishedAt,
                    List.of(),
                    failure)));

    assertTrue(succeededWithoutFailure instanceof LedgerJournalEntry.Succeeded);
    assertEquals(stepId("post"), rejectedJournal.terminalStep().stepId());
    assertEquals(stepId("post"), rejectedJournal.requiredFailedStep().stepId());
    assertThrows(
        IllegalStateException.class,
        () ->
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(succeededWithoutFailure))
                .requiredFailedStep());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.Rejected(
                stepId(" "),
                LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
                startedAt,
                finishedAt,
                List.of(),
                failure));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.Rejected(
                stepId("post"),
                LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
                finishedAt,
                startedAt,
                List.of(),
                failure));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerExecutionJournal(startedAt, finishedAt, List.of()));
  }
}
