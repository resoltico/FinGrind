package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.LedgerJournalKind;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Direct coverage for unexpected ledger-plan failure mapping branches. */
class LedgerPlanOutcomeMapperTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-29T10:15:30Z");

  @Test
  void unexpectedExecutionFailure_omitsDetailWhenMessageIsBlank() {
    LedgerStep step = new LedgerStep.OpenBook(stepId("open"));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
            step, FIXED_INSTANT, FIXED_INSTANT, new IllegalStateException("   "));

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedExecutionFailure_omitsDetailWhenMessageIsNull() {
    LedgerStep step = new LedgerStep.OpenBook(stepId("open"));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
            step, FIXED_INSTANT, FIXED_INSTANT, new IllegalStateException());

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedPlanFailure_recordsPhaseCleanupAndPriorFailureFacts() {
    LedgerStep step = new LedgerStep.OpenBook(stepId("open"));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            LedgerBoundaryPhase.COMMIT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            step.stepId(),
            step.journalStep(),
            new IllegalStateException("commit boom"),
            new IllegalStateException("rollback boom"),
            new dev.erst.fingrind.contract.LedgerStepFailure(
                BookAdministrationRejection.wireCode(
                    new BookAdministrationRejection.BookAlreadyInitialized()),
                "already initialized",
                java.util.List.of()));

    assertEquals("unexpected-plan-failure", journalEntry.failure().code());
    assertEquals(LedgerJournalKind.PLAN_BOUNDARY, journalEntry.kind());
    assertEquals(LedgerBoundaryPhase.COMMIT, journalEntry.boundaryPhase());
    assertTrue(journalEntry.failure().message().contains("during commit after step 'open'"));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Text text
                        && "phase".equals(text.name())
                        && "commit".equals(text.value())));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Group group
                        && "cleanupFailure".equals(group.name())));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Group group
                        && "priorFailure".equals(group.name())));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Text text
                        && "triggerStepId".equals(text.name())
                        && "open".equals(text.value())));
  }

  @Test
  void unexpectedPlanFailure_omitsDetailWhenMessageIsBlank() {
    LedgerStep step = new LedgerStep.OpenBook(stepId("open"));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            LedgerBoundaryPhase.COMMIT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            step.stepId(),
            step.journalStep(),
            new IllegalStateException("   "),
            null,
            null);

    assertEquals(
        "Ledger plan execution failed unexpectedly during commit after step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedPlanFailure_omitsDetailWhenMessageIsNull() {
    LedgerStep step = new LedgerStep.OpenBook(stepId("open"));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            LedgerBoundaryPhase.COMMIT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            step.stepId(),
            step.journalStep(),
            new IllegalStateException(),
            null,
            null);

    assertEquals(
        "Ledger plan execution failed unexpectedly during commit after step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedPlanFailure_recordsAssertionDetailKindWhenTriggerWasAssertion() {
    LedgerStep step =
        new LedgerStep.Assert(
            stepId("assert-balance"),
            new dev.erst.fingrind.contract.LedgerAssertion.AccountBalanceEquals(
                new dev.erst.fingrind.core.AccountCode("1000"),
                null,
                null,
                new dev.erst.fingrind.core.Money(
                    new dev.erst.fingrind.core.CurrencyCode("EUR"),
                    new java.math.BigDecimal("10.00")),
                dev.erst.fingrind.core.BalanceSide.DEBIT));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            LedgerBoundaryPhase.ROLLBACK,
            FIXED_INSTANT,
            FIXED_INSTANT,
            step.stepId(),
            step.journalStep(),
            new IllegalStateException("rollback boom"),
            null,
            null);

    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Text text
                        && "triggerDetailKind".equals(text.name())
                        && LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS
                            .wireValue()
                            .equals(text.value())));
  }

  @Test
  void unexpectedPlanFailure_withoutTriggerStepUsesPlainBoundaryMessages() {
    var initializationCheck =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            LedgerBoundaryPhase.INITIALIZATION_CHECK,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("init boom"),
            null,
            null);
    var commit =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            LedgerBoundaryPhase.COMMIT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("commit boom"),
            null,
            null);
    var rollback =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            LedgerBoundaryPhase.ROLLBACK,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("rollback boom"),
            null,
            null);

    assertEquals(
        "Ledger plan execution failed unexpectedly during initialization-check: init boom",
        initializationCheck.failure().message());
    assertEquals(
        "Ledger plan execution failed unexpectedly during commit: commit boom",
        commit.failure().message());
    assertEquals(
        "Ledger plan execution failed unexpectedly during rollback: rollback boom",
        rollback.failure().message());
  }
}
