package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostEntryResult.Committed;
import dev.erst.fingrind.application.PostEntryResult.Rejected;
import dev.erst.fingrind.application.PostingApplicationService;
import dev.erst.fingrind.application.PostingRejection;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.sqlite.SqlitePostingFactStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Fuzzes single-book SQLite commit and reload invariants using arbitrary filesystem paths. */
public class SqliteBookRoundTripFuzzTest {
  @FuzzTest
  void roundTripSingleBook(FuzzedDataProvider data) throws IOException {
    byte[] input = data.consumeRemainingAsBytes();
    try {
      PostEntryCommand command = CliFuzzSupport.readPostEntryCommand(input);
      Path bookPath =
          Files.createTempDirectory("fingrind-jazzer-book-")
              .resolve("arbitrary")
              .resolve("entity-book.sqlite");

      try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath)) {
        PostingApplicationService applicationService =
            new PostingApplicationService(
                postingFactStore,
                CliFuzzSupport.postingIdGenerator(input),
                CliFuzzSupport.fixedClock());
        PostEntryResult committedResult = applicationService.commit(command);
        if (committedResult instanceof Committed committed) {
          try (SqlitePostingFactStore reloadedStore = new SqlitePostingFactStore(bookPath)) {
            Optional<PostingFact> storedPosting =
                reloadedStore.findByIdempotency(command.requestProvenance().idempotencyKey());
            if (storedPosting.isEmpty()) {
              throw new IllegalStateException("Committed posting fact was not persisted to SQLite.");
            }
            PostingFact postingFact = storedPosting.orElseThrow();
            if (!postingFact.postingId().equals(committed.postingId())) {
              throw new IllegalStateException("Reloaded posting id differs from the commit result.");
            }
            if (!postingFact.journalEntry().equals(command.journalEntry())) {
              throw new IllegalStateException(
                  "Reloaded journal entry differs from the parsed command.");
            }
            if (!postingFact.reversalReference().equals(command.reversalReference())) {
              throw new IllegalStateException(
                  "Reloaded reversal differs from the parsed command.");
            }
            if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
              throw new IllegalStateException(
                  "Reloaded request provenance differs from the parsed command.");
            }
            if (!postingFact
                .provenance()
                .recordedAt()
                .equals(CliFuzzSupport.fixedClock().instant())) {
              throw new IllegalStateException(
                  "Reloaded recorded-at differs from the deterministic clock.");
            }
            if (postingFact.provenance().sourceChannel() != command.sourceChannel()) {
              throw new IllegalStateException(
                  "Reloaded source channel differs from the parsed command.");
            }

            PostingApplicationService duplicateService =
                new PostingApplicationService(
                    reloadedStore,
                    CliFuzzSupport.postingIdGenerator(input),
                    CliFuzzSupport.fixedClock());
            PostEntryResult duplicateResult = duplicateService.commit(command);
            if (!(duplicateResult instanceof Rejected rejected)) {
              throw new IllegalStateException("Duplicate SQLite commit should be rejected.");
            }
            if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
              throw new IllegalStateException(
                  "Duplicate SQLite commit returned the wrong rejection code.");
            }
          }
        } else if (committedResult instanceof Rejected rejected) {
          if (postingFactStore.findByIdempotency(command.requestProvenance().idempotencyKey()).isPresent()) {
            throw new IllegalStateException("Rejected SQLite command must not persist a posting fact.");
          }
          PostEntryResult repeatedResult = applicationService.commit(command);
          if (!(repeatedResult instanceof Rejected repeatedRejected)) {
            throw new IllegalStateException("Rejected SQLite command should remain rejected.");
          }
          if (!repeatedRejected.rejection().equals(rejected.rejection())) {
            throw new IllegalStateException("Repeated SQLite rejection changed unexpectedly.");
          }
        } else {
          throw new IllegalStateException("Unexpected SQLite commit result type.");
        }
      }
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }
}
