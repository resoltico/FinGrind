package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.LedgerPlanService;
import java.util.List;
import java.util.Objects;

/** Shared execution assertions for Jazzer harnesses that parse and run ledger plans. */
public final class LedgerPlanFuzzAssertions {
  private LedgerPlanFuzzAssertions() {}

  /** Stable execution summary returned after one parsed ledger plan is executed and asserted. */
  public record ExecutionSnapshot(
      String executionStatus,
      int journalStepCount,
      int listQueryStepCount,
      int structuredListQueryStepCount) {
    /** Validates one execution summary. */
    public ExecutionSnapshot {
      executionStatus = requireText(executionStatus, "executionStatus");
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
          new LedgerPlanService(
                  bookSession, CliFuzzFixtures.postingIdGenerator(input), CliFuzzFixtures.fixedClock())
              .execute(plan);
      return assertPlanResult(plan, result);
    }
  }

  private static ExecutionSnapshot assertPlanResult(LedgerPlan plan, LedgerPlanResult result) {
    if (!result.planId().equals(plan.planId())) {
      throw new IllegalStateException("Ledger plan execution changed the plan id.");
    }
    List<LedgerJournalEntry> journalSteps = result.journal().steps();
    if (journalSteps.isEmpty()) {
      throw new IllegalStateException("Ledger plan execution produced an empty journal.");
    }
    if (journalSteps.size() > plan.steps().size()) {
      throw new IllegalStateException("Ledger plan journal exceeded the declared step count.");
    }
    if (result.journal().finishedAt().isBefore(result.journal().startedAt())) {
      throw new IllegalStateException("Ledger plan journal finished before it started.");
    }
    if (result.status() == dev.erst.fingrind.contract.LedgerPlanStatus.SUCCEEDED
        && journalSteps.size() != plan.steps().size()) {
      throw new IllegalStateException("Successful ledger plan execution omitted journal steps.");
    }
    switch (result.status()) {
      case SUCCEEDED -> {
        if (journalSteps.stream().anyMatch(entry -> entry.status() != dev.erst.fingrind.contract.LedgerStepStatus.SUCCEEDED)) {
          throw new IllegalStateException(
              "Successful ledger plan execution retained a failed step status.");
        }
      }
      case REJECTED -> {
        if (journalSteps.getLast().status() != dev.erst.fingrind.contract.LedgerStepStatus.REJECTED) {
          throw new IllegalStateException("Rejected ledger plan did not end with a rejected step.");
        }
      }
      case ASSERTION_FAILED -> {
        if (journalSteps.getLast().status()
            != dev.erst.fingrind.contract.LedgerStepStatus.ASSERTION_FAILED) {
          throw new IllegalStateException(
              "Assertion-failed ledger plan did not end with an assertion-failed step.");
        }
      }
    }

    int listQueryStepCount = 0;
    int structuredListQueryStepCount = 0;
    for (int index = 0; index < journalSteps.size(); index++) {
      LedgerJournalEntry journalEntry = journalSteps.get(index);
      var declaredStep = plan.steps().get(index);
      if (!journalEntry.stepId().equals(declaredStep.stepId())) {
        throw new IllegalStateException("Ledger plan journal changed step order or identity.");
      }
      if (journalEntry.kind() != declaredStep.kind()) {
        throw new IllegalStateException("Ledger plan journal changed the declared step kind.");
      }
      if (journalEntry.finishedAt().isBefore(journalEntry.startedAt())) {
        throw new IllegalStateException("Ledger plan step finished before it started.");
      }
      if (journalEntry.kind() == LedgerStepKind.LIST_ACCOUNTS
          || journalEntry.kind() == LedgerStepKind.LIST_POSTINGS) {
        listQueryStepCount++;
        assertStructuredListQueryFacts(journalEntry);
        structuredListQueryStepCount++;
      }
    }
    return new ExecutionSnapshot(
        result.status().name(),
        journalSteps.size(),
        listQueryStepCount,
        structuredListQueryStepCount);
  }

  private static void assertStructuredListQueryFacts(LedgerJournalEntry journalEntry) {
    int count = requiredCountFact(journalEntry.facts(), "count").value();
    int pageLimit = requiredCountFact(journalEntry.facts(), "pageLimit").value();
    boolean hasMore = requiredFlagFact(journalEntry.facts(), "hasMore").value();
    String expectedGroupName =
        journalEntry.kind() == LedgerStepKind.LIST_ACCOUNTS ? "account" : "posting";
    long groupCount = groupFactCount(journalEntry.facts(), expectedGroupName);
    if (count < 0 || count > pageLimit) {
      throw new IllegalStateException("Ledger plan list-query facts reported an invalid count.");
    }
    if (pageLimit <= 0) {
      throw new IllegalStateException("Ledger plan list-query facts reported a non-positive limit.");
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

  private static LedgerFact.Count requiredCountFact(List<LedgerFact> facts, String factName) {
    List<LedgerFact.Count> counts = requiredTypedFacts(facts, factName, LedgerFact.Count.class);
    if (counts.size() != 1) {
      throw new IllegalStateException(
          "Ledger plan facts must contain exactly one count fact named '" + factName + "'.");
    }
    return counts.getFirst();
  }

  private static LedgerFact.Flag requiredFlagFact(List<LedgerFact> facts, String factName) {
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

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return normalized;
  }
}
