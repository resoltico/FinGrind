package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import dev.erst.fingrind.executor.InMemoryBookSession;
import java.util.List;
import java.util.Objects;

/** Shared execution assertions for Jazzer harnesses that parse and run ledger plans. */
public final class LedgerPlanFuzzAssertions {
  private LedgerPlanFuzzAssertions() {}

  private record JournalScanSummary(int listQueryStepCount, int structuredListQueryStepCount) {
    private JournalScanSummary {
      if (listQueryStepCount < 0
          || structuredListQueryStepCount < 0
          || structuredListQueryStepCount > listQueryStepCount) {
        throw new IllegalArgumentException("Ledger journal scan counts must be non-negative.");
      }
    }

    private JournalScanSummary recordStructuredListQuery() {
      return new JournalScanSummary(listQueryStepCount + 1, structuredListQueryStepCount + 1);
    }

    private JournalScanSummary recordRejectedListQuery() {
      return new JournalScanSummary(listQueryStepCount + 1, structuredListQueryStepCount);
    }
  }

  /** Stable execution summary returned after one parsed ledger plan is executed and asserted. */
  public record ExecutionSnapshot(
      LedgerPlanStatus executionStatus,
      int journalStepCount,
      int listQueryStepCount,
      int structuredListQueryStepCount) {
    /** Validates one execution summary. */
    public ExecutionSnapshot {
      Objects.requireNonNull(executionStatus, "executionStatus must not be null");
      if (journalStepCount < 0
          || listQueryStepCount < 0
          || structuredListQueryStepCount < 0
          || structuredListQueryStepCount > listQueryStepCount) {
        throw new IllegalArgumentException("Ledger plan execution counts must be non-negative.");
      }
    }
  }

  /** Executes one parsed ledger plan and asserts public journal invariants. */
  public static ExecutionSnapshot executeAndAssert(LedgerPlan plan, byte[] input) {
    Objects.requireNonNull(plan, "plan");
    Objects.requireNonNull(input, "input");
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanResult result =
          CliFuzzWorkflowFixtures.ledgerPlanService(
                  bookSession,
                  bookSession,
                  bookSession,
                  bookSession,
                  bookSession,
                  CliFuzzFixtures.postingIdGenerator(input))
              .execute(plan);
      return assertPlanResult(plan, result);
    }
  }

  static ExecutionSnapshot assertPlanResult(LedgerPlan plan, LedgerPlanResult result) {
    List<LedgerJournalEntry> journalSteps = result.journal().steps();
    assertPlanIdentity(plan, result);
    assertJournalBounds(plan, result.status(), journalSteps);
    JournalScanSummary journalSummary = scanJournal(plan, journalSteps);
    return new ExecutionSnapshot(
        result.status(),
        journalSteps.size(),
        journalSummary.listQueryStepCount(),
        journalSummary.structuredListQueryStepCount());
  }

  private static void assertPlanIdentity(LedgerPlan plan, LedgerPlanResult result) {
    if (!result.planId().equals(plan.planId())) {
      throw new IllegalStateException("Ledger plan execution changed the plan id.");
    }
  }

  private static void assertJournalBounds(
      LedgerPlan plan, LedgerPlanStatus status, List<LedgerJournalEntry> journalSteps) {
    boolean terminalBoundary = journalSteps.getLast().kind() == LedgerJournalKind.PLAN_BOUNDARY;
    int allowedJournalSteps = plan.steps().size() + (terminalBoundary ? 1 : 0);
    if (journalSteps.size() > allowedJournalSteps) {
      throw new IllegalStateException("Ledger plan journal exceeded the declared step count.");
    }
    if (status == LedgerPlanStatus.SUCCEEDED && journalSteps.size() != plan.steps().size()) {
      throw new IllegalStateException("Successful ledger plan execution omitted journal steps.");
    }
  }

  private static JournalScanSummary scanJournal(
      LedgerPlan plan, List<LedgerJournalEntry> journalSteps) {
    JournalScanSummary summary = new JournalScanSummary(0, 0);
    int declaredIndex = 0;
    for (int index = 0; index < journalSteps.size(); index++) {
      LedgerJournalEntry journalEntry = journalSteps.get(index);
      if (journalEntry.kind() == LedgerJournalKind.PLAN_BOUNDARY) {
        assertTerminalBoundary(index, journalSteps.size());
        continue;
      }
      assertDeclaredStep(plan, journalEntry, declaredIndex);
      declaredIndex++;
      summary = tallyListQuerySummary(summary, journalEntry);
    }
    return summary;
  }

  private static void assertTerminalBoundary(int index, int journalSize) {
    if (index != journalSize - 1) {
      throw new IllegalStateException("Ledger plan boundary journal entries must be terminal.");
    }
  }

  private static JournalScanSummary tallyListQuerySummary(
      JournalScanSummary summary, LedgerJournalEntry journalEntry) {
    if (!isListQueryKind(journalEntry.kind())) {
      return summary;
    }
    if (journalEntry.status() == LedgerStepStatus.SUCCEEDED) {
      LedgerPlanListQueryAssertions.assertStructuredListQueryFacts(journalEntry);
      return summary.recordStructuredListQuery();
    }
    LedgerPlanListQueryAssertions.assertRejectedListQueryFacts(journalEntry);
    return summary.recordRejectedListQuery();
  }

  private static boolean isListQueryKind(LedgerJournalKind kind) {
    return kind == LedgerJournalKind.LIST_ACCOUNTS || kind == LedgerJournalKind.LIST_POSTINGS;
  }

  private static void assertDeclaredStep(
      LedgerPlan plan, LedgerJournalEntry journalEntry, int declaredIndex) {
    var declaredStep = plan.steps().get(declaredIndex);
    if (!journalEntry.stepId().equals(declaredStep.stepId())) {
      throw new IllegalStateException("Ledger plan journal changed step order or identity.");
    }
    if (!journalEntry.kind().wireValue().equals(declaredStep.kind().wireValue())) {
      throw new IllegalStateException("Ledger plan journal changed the declared step kind.");
    }
  }
}
