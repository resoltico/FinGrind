package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.workflow.LedgerFact;
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

  static void assertStructuredListQueryFacts(LedgerJournalEntry journalEntry) {
    int count = requiredCountFact(journalEntry.facts(), "count").value();
    int pageLimit = requiredCountFact(journalEntry.facts(), "pageLimit").value();
    boolean hasMore = requiredFlagFact(journalEntry.facts(), "hasMore").value();
    String expectedGroupName = expectedListQueryGroupName(journalEntry.kind());
    long groupCount = groupFactCount(journalEntry.facts(), expectedGroupName);
    if (count < 0 || count > pageLimit) {
      throw new IllegalStateException("Ledger plan list-query facts reported an invalid count.");
    }
    if (pageLimit <= 0) {
      throw new IllegalStateException(
          "Ledger plan list-query facts reported a non-positive limit.");
    }
    if (groupCount != count) {
      throw new IllegalStateException(
          "Ledger plan list-query facts lost row groups for the returned page.");
    }
    boolean hasCursorFact = hasFactNamed(journalEntry.facts(), "nextCursor");
    if (hasMore && !hasCursorFact) {
      throw new IllegalStateException(
          "Ledger plan list-query facts omitted nextCursor for a continued page.");
    }
    if (!hasMore && hasCursorFact) {
      throw new IllegalStateException(
          "Ledger plan list-query facts retained nextCursor for a terminal page.");
    }
  }

  static void assertRejectedListQueryFacts(LedgerJournalEntry journalEntry) {
    journalEntry.requiredFailure();
    String expectedGroupName = expectedListQueryGroupName(journalEntry.kind());
    for (String factName :
        List.of("count", "pageLimit", "hasMore", "nextCursor", expectedGroupName)) {
      if (hasFactNamed(journalEntry.facts(), factName)) {
        throw new IllegalStateException(
            "Rejected ledger plan list-query steps must not retain success-only fact '"
                + factName
                + "'.");
      }
    }
  }

  static String expectedListQueryGroupName(LedgerJournalKind kind) {
    return switch (kind) {
      case LIST_ACCOUNTS -> "account";
      case LIST_POSTINGS -> "posting";
      case OPEN_BOOK,
          DECLARE_ACCOUNT,
          PREFLIGHT_ENTRY,
          POST_ENTRY,
          INSPECT_BOOK,
          GET_POSTING,
          ACCOUNT_BALANCE,
          ASSERT,
          PLAN_BOUNDARY ->
          throw new IllegalArgumentException(
              "Expected a list-query journal kind but received '%s'.".formatted(kind.wireValue()));
    };
  }

  static LedgerFact.Count requiredCountFact(List<LedgerFact> facts, String factName) {
    List<LedgerFact.Count> counts = requiredTypedFacts(facts, factName, LedgerFact.Count.class);
    if (counts.size() != 1) {
      throw new IllegalStateException(
          "Ledger plan facts must contain exactly one count fact named '" + factName + "'.");
    }
    return counts.getFirst();
  }

  static LedgerFact.Flag requiredFlagFact(List<LedgerFact> facts, String factName) {
    List<LedgerFact.Flag> flags = requiredTypedFacts(facts, factName, LedgerFact.Flag.class);
    if (flags.size() != 1) {
      throw new IllegalStateException(
          "Ledger plan facts must contain exactly one flag fact named '" + factName + "'.");
    }
    return flags.getFirst();
  }

  private static <T extends LedgerFact> List<T> requiredTypedFacts(
      List<LedgerFact> facts, String factName, Class<T> expectedType) {
    Objects.requireNonNull(facts, "facts");
    Objects.requireNonNull(factName, "factName");
    Objects.requireNonNull(expectedType, "expectedType");
    return facts.stream()
        .filter(fact -> fact.name().equals(factName))
        .map(
            fact -> {
              if (!expectedType.isInstance(fact)) {
                throw new IllegalStateException(
                    "Ledger plan fact '"
                        + factName
                        + "' used the wrong fact kind: "
                        + fact.getClass().getSimpleName());
              }
              return expectedType.cast(fact);
            })
        .toList();
  }

  private static long groupFactCount(List<LedgerFact> facts, String factName) {
    return facts.stream()
        .filter(LedgerFact.Group.class::isInstance)
        .map(LedgerFact.Group.class::cast)
        .filter(group -> group.name().equals(factName))
        .count();
  }

  private static boolean hasFactNamed(List<LedgerFact> facts, String factName) {
    return facts.stream().anyMatch(fact -> fact.name().equals(factName));
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
      assertStructuredListQueryFacts(journalEntry);
      return summary.recordStructuredListQuery();
    }
    assertRejectedListQueryFacts(journalEntry);
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
