package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused coverage for the human-readable execute-plan renderer. */
class CliPlanTextRendererTest extends CliResponseWriterTestSupport {
  @Test
  void renderLedgerPlanResult_summaryModeStopsBeforeTheJournalSection() {
    String rendered =
        CliPlanTextRenderer.renderLedgerPlanResult(succeededPlanResult(), PlanResultDetail.SUMMARY);

    assertTrue(rendered.contains("Execute Plan"));
    assertTrue(rendered.contains("Plan id"));
    assertTrue(rendered.contains("succeeded"));
    assertFalse(rendered.contains("Journal"));
  }

  @Test
  void renderLedgerPlanResult_fullModeRendersSucceededJournalFactsAndBoundarySteps() {
    String rendered =
        CliPlanTextRenderer.renderLedgerPlanResult(succeededPlanResult(), PlanResultDetail.FULL);

    assertTrue(rendered.contains("Journal"));
    assertTrue(rendered.contains("01. Ensure Book [succeeded]"));
    assertTrue(rendered.contains("02. Plan Boundary (Commit) [succeeded]"));
    assertTrue(rendered.contains("Outcome"));
    assertTrue(rendered.contains("Entity name"));
    assertTrue(rendered.contains("Acme Studio"));
    assertTrue(rendered.contains("Functional currency"));
    assertTrue(rendered.contains("Fiscal year start"));
    assertFalse(rendered.contains("Outcome shape"));
    assertFalse(rendered.contains("Succeeded"));
  }

  @Test
  void renderLedgerPlanResult_fullModeRendersFailureSummariesAndFailureFacts() {
    String rendered =
        CliPlanTextRenderer.renderLedgerPlanResult(
            assertionFailedPlanResult(), PlanResultDetail.FULL);

    assertTrue(rendered.contains("assertion-failed"));
    assertTrue(rendered.contains("Failed step id"));
    assertTrue(rendered.contains("Failure code"));
    assertTrue(rendered.contains("Failure message"));
    assertTrue(rendered.contains("02. Assert (Assert Account Balance) [assertion-failed]"));
    assertTrue(rendered.contains("Failure details"));
    assertTrue(rendered.contains("100.00 EUR"));
  }

  @Test
  void renderLedgerPlanResult_fullModeOmitsFailureFactsWhenNoneWereRecorded() {
    String rendered =
        CliPlanTextRenderer.renderLedgerPlanResult(
            rejectedPlanResultWithoutFailureFacts(), PlanResultDetail.FULL);

    assertTrue(rendered.contains("rejected"));
    assertTrue(rendered.contains("Failure code"));
    assertFalse(rendered.contains("Failure details"));
  }

  private static LedgerPlanResult.Succeeded succeededPlanResult() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant openFinishedAt = Instant.parse("2026-04-17T10:15:31Z");
    Instant commitFinishedAt = Instant.parse("2026-04-17T10:15:32Z");
    LedgerJournalEntry.Succeeded openStep =
        new LedgerJournalEntry.Succeeded(
            stepId("open-book"),
            LedgerJournalStep.standard(LedgerStepKind.ENSURE_BOOK),
            startedAt,
            openFinishedAt,
            List.of(
                LedgerFact.text("initializedAt", openFinishedAt.toString()),
                LedgerFact.text("entityName", "Acme Studio"),
                LedgerFact.text("functionalCurrency", "EUR"),
                LedgerFact.text("fiscalYearStart", "01-01")));
    LedgerJournalEntry.Succeeded commitStep =
        new LedgerJournalEntry.Succeeded(
            stepId("@plan-boundary:commit"),
            LedgerJournalStep.boundary(LedgerBoundaryPhase.COMMIT),
            openFinishedAt,
            commitFinishedAt,
            List.of());
    return new LedgerPlanResult.Succeeded(
        planId("plan-success"),
        new LedgerExecutionJournal(startedAt, commitFinishedAt, List.of(openStep, commitStep)));
  }

  private static LedgerPlanResult.AssertionFailed assertionFailedPlanResult() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant queryFinishedAt = Instant.parse("2026-04-17T10:15:31Z");
    Instant failureFinishedAt = Instant.parse("2026-04-17T10:15:32Z");
    LedgerJournalEntry.Succeeded queryStep =
        new LedgerJournalEntry.Succeeded(
            stepId("account-balance"),
            LedgerJournalStep.standard(LedgerStepKind.ACCOUNT_BALANCE),
            startedAt,
            queryFinishedAt,
            List.of(LedgerFact.text("accountCode", "1000")));
    LedgerJournalEntry.AssertionFailed failedStep =
        new LedgerJournalEntry.AssertionFailed(
            stepId("assert-balance"),
            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS),
            queryFinishedAt,
            failureFinishedAt,
            List.of(LedgerFact.money("actualNetAmount", new MonetaryAmount("EUR", "9000"))),
            new LedgerStepFailure(
                "assertion-failed",
                "Balance mismatch.",
                List.of(
                    LedgerFact.group(
                        "expected",
                        List.of(
                            LedgerFact.text("currency", "EUR"),
                            LedgerFact.money("netAmount", new MonetaryAmount("EUR", "10000")))))));
    return new LedgerPlanResult.AssertionFailed(
        planId("plan-failure"),
        new LedgerExecutionJournal(startedAt, failureFinishedAt, List.of(queryStep, failedStep)));
  }

  private static LedgerPlanResult.Rejected rejectedPlanResultWithoutFailureFacts() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant queryFinishedAt = Instant.parse("2026-04-17T10:15:31Z");
    Instant failureFinishedAt = Instant.parse("2026-04-17T10:15:32Z");
    LedgerJournalEntry.Succeeded queryStep =
        new LedgerJournalEntry.Succeeded(
            stepId("inspect-book"),
            LedgerJournalStep.standard(LedgerStepKind.INSPECT_BOOK),
            startedAt,
            queryFinishedAt,
            List.of());
    LedgerJournalEntry.Rejected failedStep =
        new LedgerJournalEntry.Rejected(
            stepId("declare-account"),
            LedgerJournalStep.standard(LedgerStepKind.DECLARE_ACCOUNT),
            queryFinishedAt,
            failureFinishedAt,
            List.of(),
            new LedgerStepFailure(
                "administration-book-not-initialized", "Book is not initialized.", List.of()));
    return new LedgerPlanResult.Rejected(
        planId("plan-rejected"),
        new LedgerExecutionJournal(startedAt, failureFinishedAt, List.of(queryStep, failedStep)));
  }
}
