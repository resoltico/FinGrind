package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostEntryResult.Committed;
import dev.erst.fingrind.application.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.application.PostEntryResult.Rejected;
import dev.erst.fingrind.application.PostingApplicationService;
import dev.erst.fingrind.application.PostingRejectionCode;
import dev.erst.fingrind.runtime.InMemoryPostingFactStore;
import dev.erst.fingrind.runtime.PostingFact;
import java.util.Optional;
import org.junit.jupiter.api.Tag;

/** Fuzzes posting workflow invariants above the runtime seam using an in-memory book. */
@Tag("jazzer")
public class PostingWorkflowFuzzTest {
  @FuzzTest
  void exercisePostingWorkflow(FuzzedDataProvider data) {
    byte[] input = data.consumeRemainingAsBytes();
    try {
      PostEntryCommand command = CliFuzzSupport.readPostEntryCommand(input);
      InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
      PostingApplicationService applicationService =
          new PostingApplicationService(postingFactStore, CliFuzzSupport.postingIdSupplier(input));

      PostEntryResult preflight = applicationService.preflight(command);
      if (!(preflight instanceof PreflightAccepted accepted)) {
        throw new IllegalStateException("Expected a preflight acceptance for a fresh valid book.");
      }
      if (!accepted.idempotencyKey().equals(command.provenance().idempotencyKey())) {
        throw new IllegalStateException("Preflight changed the idempotency key.");
      }
      if (!accepted.effectiveDate().equals(command.journalEntry().effectiveDate())) {
        throw new IllegalStateException("Preflight changed the effective date.");
      }

      PostEntryResult committedResult = applicationService.commit(command);
      if (!(committedResult instanceof Committed committed)) {
        throw new IllegalStateException("Expected a committed result for a fresh valid book.");
      }

      Optional<PostingFact> storedPosting =
          postingFactStore.findByIdempotency(command.provenance().idempotencyKey());
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
      if (!postingFact.provenance().equals(command.provenance())) {
        throw new IllegalStateException("Stored provenance differs from the parsed command.");
      }

      PostEntryResult duplicateResult = applicationService.commit(command);
      if (!(duplicateResult instanceof Rejected rejected)) {
        throw new IllegalStateException("Duplicate commit should be rejected.");
      }
      if (rejected.code() != PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY) {
        throw new IllegalStateException("Duplicate commit returned the wrong rejection code.");
      }
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }
}
