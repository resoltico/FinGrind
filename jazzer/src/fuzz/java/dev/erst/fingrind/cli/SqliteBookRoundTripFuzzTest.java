package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostEntryResult.Committed;
import dev.erst.fingrind.application.PostEntryResult.Rejected;
import dev.erst.fingrind.application.PostingApplicationService;
import dev.erst.fingrind.application.PostingRejectionCode;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.sqlite.SqlitePostingFactStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Tag;

/** Fuzzes single-book SQLite commit and reload invariants using arbitrary filesystem paths. */
@Tag("jazzer")
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

      Committed committed;
      try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath)) {
        PostingApplicationService applicationService =
            new PostingApplicationService(postingFactStore, CliFuzzSupport.postingIdSupplier(input));
        PostEntryResult committedResult = applicationService.commit(command);
        if (!(committedResult instanceof Committed committedResultValue)) {
          throw new IllegalStateException("Expected a committed result for a fresh valid SQLite book.");
        }
        committed = committedResultValue;
      }

      try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath)) {
        Optional<PostingFact> storedPosting =
            postingFactStore.findByIdempotency(command.provenance().idempotencyKey());
        if (storedPosting.isEmpty()) {
          throw new IllegalStateException("Committed posting fact was not persisted to SQLite.");
        }
        PostingFact postingFact = storedPosting.orElseThrow();
        if (!postingFact.postingId().equals(committed.postingId())) {
          throw new IllegalStateException("Reloaded posting id differs from the commit result.");
        }
        if (!postingFact.journalEntry().equals(command.journalEntry())) {
          throw new IllegalStateException("Reloaded journal entry differs from the parsed command.");
        }
        if (!postingFact.provenance().equals(command.provenance())) {
          throw new IllegalStateException("Reloaded provenance differs from the parsed command.");
        }

        PostingApplicationService applicationService =
            new PostingApplicationService(postingFactStore, CliFuzzSupport.postingIdSupplier(input));
        PostEntryResult duplicateResult = applicationService.commit(command);
        if (!(duplicateResult instanceof Rejected rejected)) {
          throw new IllegalStateException("Duplicate SQLite commit should be rejected.");
        }
        if (rejected.code() != PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY) {
          throw new IllegalStateException("Duplicate SQLite commit returned the wrong rejection code.");
        }
      }
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }
}
