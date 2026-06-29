package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import java.util.List;
import java.util.Objects;

/** Shared assertions for structured and rejected ledger-plan list-query journal facts. */
final class LedgerPlanListQueryAssertions {
  private LedgerPlanListQueryAssertions() {}

  static void assertStructuredListQueryFacts(LedgerJournalEntry journalEntry) {
    List<LedgerFact> facts = journalEntry.facts();
    int count = requiredCountFact(facts, "count").value();
    int pageLimit = requiredCountFact(facts, "pageLimit").value();
    requireValidListQueryBounds(count, pageLimit);
    requireReturnedGroupCount(journalEntry.kind(), facts, count);
    requireCursorConsistency(requiredFlagFact(facts, "hasMore").value(), facts);
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
    if (kind == LedgerJournalKind.LIST_ACCOUNTS) {
      return "account";
    }
    if (kind == LedgerJournalKind.LIST_POSTINGS) {
      return "posting";
    }
    throw new IllegalArgumentException(
        "Expected a list-query journal kind but received '%s'.".formatted(kind.wireValue()));
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

  private static void requireValidListQueryBounds(int count, int pageLimit) {
    if (count < 0 || count > pageLimit) {
      throw new IllegalStateException("Ledger plan list-query facts reported an invalid count.");
    }
    if (pageLimit <= 0) {
      throw new IllegalStateException(
          "Ledger plan list-query facts reported a non-positive limit.");
    }
  }

  private static void requireReturnedGroupCount(
      LedgerJournalKind kind, List<LedgerFact> facts, int expectedCount) {
    long groupCount = groupFactCount(facts, expectedListQueryGroupName(kind));
    if (groupCount != expectedCount) {
      throw new IllegalStateException(
          "Ledger plan list-query facts lost row groups for the returned page.");
    }
  }

  private static void requireCursorConsistency(boolean hasMore, List<LedgerFact> facts) {
    boolean hasCursorFact = hasFactNamed(facts, "nextCursor");
    if (hasMore && !hasCursorFact) {
      throw new IllegalStateException(
          "Ledger plan list-query facts omitted nextCursor for a continued page.");
    }
    if (!hasMore && hasCursorFact) {
      throw new IllegalStateException(
          "Ledger plan list-query facts retained nextCursor for a terminal page.");
    }
  }
}
