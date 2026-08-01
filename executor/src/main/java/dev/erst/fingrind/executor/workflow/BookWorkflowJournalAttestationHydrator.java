package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.AttestationPostingCommitmentProjection;
import dev.erst.fingrind.executor.spi.AttestationPostingCommitmentStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reprojects posting-query journal facts after a plan's aggregate attestation is appended. */
final class BookWorkflowJournalAttestationHydrator {
  private BookWorkflowJournalAttestationHydrator() {}

  /**
   * Replaces each completed posting-query fact's commitment with the authenticated current-chain
   * projection while its transaction is still open.
   */
  static List<BookWorkflowJournalEntry> hydrate(
      List<BookWorkflowJournalEntry> entries,
      AttestationPostingCommitmentStore commitmentStore,
      AttestationCommit aggregateCommit) {
    List<BookWorkflowJournalEntry> checkedEntries =
        List.copyOf(Objects.requireNonNull(entries, "entries"));
    Objects.requireNonNull(commitmentStore, "commitmentStore");
    AttestationCommit checkedAggregateCommit =
        Objects.requireNonNull(aggregateCommit, "aggregateCommit");
    Set<PostingId> freshPostingIds = freshPostingIds(checkedEntries);
    Set<PostingId> queriedPostingIds = queriedPostingIds(checkedEntries);
    if (queriedPostingIds.isEmpty()) {
      return checkedEntries;
    }
    Map<PostingId, AttestationCommit> commitments =
        AttestationPostingCommitmentProjection.resolve(commitmentStore, queriedPostingIds);
    List<BookWorkflowJournalEntry> hydratedEntries = new ArrayList<>(checkedEntries.size());
    for (BookWorkflowJournalEntry entry : checkedEntries) {
      hydratedEntries.add(
          hydrateEntry(entry, commitments, freshPostingIds, checkedAggregateCommit));
    }
    return List.copyOf(hydratedEntries);
  }

  private static Set<PostingId> freshPostingIds(List<BookWorkflowJournalEntry> entries) {
    Set<PostingId> postingIds = new LinkedHashSet<>();
    for (BookWorkflowJournalEntry entry : entries) {
      if (entry instanceof BookWorkflowJournalEntry.Succeeded succeeded
          && succeeded.descriptor() instanceof BookWorkflowJournalDescriptor.Step stepDescriptor
          && stepDescriptor.step() instanceof BookWorkflowStep.PostEntry
          && !BookWorkflowPostingAttestationFactProjection.idempotentReplayFrom(
              succeeded.facts())) {
        postingIds.add(
            BookWorkflowPostingAttestationFactProjection.postingIdFrom(succeeded.facts()));
      }
    }
    return Set.copyOf(postingIds);
  }

  private static Set<PostingId> queriedPostingIds(List<BookWorkflowJournalEntry> entries) {
    Set<PostingId> postingIds = new LinkedHashSet<>();
    for (BookWorkflowJournalEntry entry : entries) {
      if (entry instanceof BookWorkflowJournalEntry.Succeeded succeeded) {
        switch (succeeded.descriptor()) {
          case BookWorkflowJournalDescriptor.Step stepDescriptor ->
              collectQueryPostingIds(stepDescriptor.step(), succeeded.facts(), postingIds);
          case BookWorkflowJournalDescriptor.Boundary _ -> {}
        }
      }
    }
    return Set.copyOf(postingIds);
  }

  private static void collectQueryPostingIds(
      BookWorkflowStep step, List<BookWorkflowFact> facts, Set<PostingId> postingIds) {
    if (step instanceof BookWorkflowStep.GetPosting) {
      postingIds.add(BookWorkflowPostingAttestationFactProjection.postingIdFrom(facts));
      return;
    }
    if (step instanceof BookWorkflowStep.ListPostings) {
      for (BookWorkflowFact fact : facts) {
        if (fact instanceof BookWorkflowFact.Group group && "posting".equals(group.name())) {
          postingIds.add(BookWorkflowPostingAttestationFactProjection.postingIdFrom(group.facts()));
        }
      }
    }
  }

  private static BookWorkflowJournalEntry hydrateEntry(
      BookWorkflowJournalEntry entry,
      Map<PostingId, AttestationCommit> commitments,
      Set<PostingId> freshPostingIds,
      AttestationCommit aggregateCommit) {
    if (!(entry instanceof BookWorkflowJournalEntry.Succeeded succeeded)) {
      return entry;
    }
    return switch (succeeded.descriptor()) {
      case BookWorkflowJournalDescriptor.Step stepDescriptor ->
          hydrateStepEntry(
              succeeded, stepDescriptor.step(), commitments, freshPostingIds, aggregateCommit);
      case BookWorkflowJournalDescriptor.Boundary _ -> succeeded;
    };
  }

  private static BookWorkflowJournalEntry.Succeeded hydrateStepEntry(
      BookWorkflowJournalEntry.Succeeded entry,
      BookWorkflowStep step,
      Map<PostingId, AttestationCommit> commitments,
      Set<PostingId> freshPostingIds,
      AttestationCommit aggregateCommit) {
    if (!(step instanceof BookWorkflowStep.GetPosting)
        && !(step instanceof BookWorkflowStep.ListPostings)) {
      return entry;
    }
    List<BookWorkflowFact> hydratedFacts;
    if (step instanceof BookWorkflowStep.GetPosting) {
      hydratedFacts =
          BookWorkflowPostingAttestationFactProjection.hydrate(
              entry.facts(), commitments, freshPostingIds, aggregateCommit);
    } else {
      hydratedFacts =
          hydratePostingPageFacts(entry.facts(), commitments, freshPostingIds, aggregateCommit);
    }
    return new BookWorkflowJournalEntry.Succeeded(
        entry.stepId(), entry.descriptor(), entry.startedAt(), entry.finishedAt(), hydratedFacts);
  }

  private static List<BookWorkflowFact> hydratePostingPageFacts(
      List<BookWorkflowFact> facts,
      Map<PostingId, AttestationCommit> commitments,
      Set<PostingId> freshPostingIds,
      AttestationCommit aggregateCommit) {
    List<BookWorkflowFact> hydratedFacts = new ArrayList<>(facts.size());
    for (BookWorkflowFact fact : facts) {
      if (fact instanceof BookWorkflowFact.Group group && "posting".equals(group.name())) {
        hydratedFacts.add(
            BookWorkflowFact.group(
                "posting",
                BookWorkflowPostingAttestationFactProjection.hydrate(
                    group.facts(), commitments, freshPostingIds, aggregateCommit)));
      } else {
        hydratedFacts.add(fact);
      }
    }
    return List.copyOf(hydratedFacts);
  }
}
