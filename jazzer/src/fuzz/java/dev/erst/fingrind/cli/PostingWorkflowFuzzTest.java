package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostEntryResult.Committed;
import dev.erst.fingrind.application.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.application.PostEntryResult.Rejected;
import dev.erst.fingrind.application.PostingApplicationService;
import dev.erst.fingrind.application.PostingRejection;
import dev.erst.fingrind.runtime.InMemoryPostingFactStore;
import dev.erst.fingrind.runtime.PostingFact;
import java.util.Optional;

/** Fuzzes posting workflow invariants above the runtime seam using an in-memory book. */
public class PostingWorkflowFuzzTest {
  @FuzzTest
  void exercisePostingWorkflow(FuzzedDataProvider data) {
    byte[] input = data.consumeRemainingAsBytes();
    try {
      PostEntryCommand command = CliFuzzSupport.readPostEntryCommand(input);
      InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
      PostingApplicationService applicationService =
          new PostingApplicationService(
              postingFactStore,
              CliFuzzSupport.postingIdGenerator(input),
              CliFuzzSupport.fixedClock());

      PostEntryResult preflight = applicationService.preflight(command);
      PostEntryResult committedResult = applicationService.commit(command);
      if (preflight instanceof PreflightAccepted accepted) {
        if (!accepted.idempotencyKey().equals(command.requestProvenance().idempotencyKey())) {
          throw new IllegalStateException("Preflight changed the idempotency key.");
        }
        if (!accepted.effectiveDate().equals(command.journalEntry().effectiveDate())) {
          throw new IllegalStateException("Preflight changed the effective date.");
        }
        if (!(committedResult instanceof Committed committed)) {
          throw new IllegalStateException("Accepted preflight should commit on a fresh valid book.");
        }

        Optional<PostingFact> storedPosting =
            postingFactStore.findByIdempotency(command.requestProvenance().idempotencyKey());
        if (storedPosting.isEmpty()) {
          throw new IllegalStateException("Committed posting fact was not persisted.");
        }
        PostingFact postingFact = storedPosting.orElseThrow();
        if (!postingFact.postingId().equals(committed.postingId())) {
          throw new IllegalStateException("Stored posting id differs from the commit result.");
        }
        if (!postingFact.journalEntry().equals(command.journalEntry())) {
          throw new IllegalStateException("Stored journal entry differs from the parsed command.");
        }
        if (!postingFact.reversalReference().equals(command.reversalReference())) {
          throw new IllegalStateException("Stored reversal differs from the parsed command.");
        }
        if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
          throw new IllegalStateException(
              "Stored request provenance differs from the parsed command.");
        }
        if (!postingFact.provenance().recordedAt().equals(CliFuzzSupport.fixedClock().instant())) {
          throw new IllegalStateException(
              "Stored recorded-at differs from the deterministic clock.");
        }
        if (postingFact.provenance().sourceChannel() != command.sourceChannel()) {
          throw new IllegalStateException("Stored source channel differs from the parsed command.");
        }

        PostEntryResult duplicateResult = applicationService.commit(command);
        if (!(duplicateResult instanceof Rejected rejected)) {
          throw new IllegalStateException("Duplicate commit should be rejected.");
        }
        if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
          throw new IllegalStateException("Duplicate commit returned the wrong rejection code.");
        }
      } else if (preflight instanceof Rejected preflightRejected) {
        if (!(committedResult instanceof Rejected commitRejected)) {
          throw new IllegalStateException("Rejected preflight should remain rejected on commit.");
        }
        if (!commitRejected.rejection().equals(preflightRejected.rejection())) {
          throw new IllegalStateException("Commit changed the deterministic rejection.");
        }
        if (postingFactStore.findByIdempotency(command.requestProvenance().idempotencyKey()).isPresent()) {
          throw new IllegalStateException("Rejected command must not persist a posting fact.");
        }
      } else {
        throw new IllegalStateException("Unexpected preflight result type.");
      }
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }
}
