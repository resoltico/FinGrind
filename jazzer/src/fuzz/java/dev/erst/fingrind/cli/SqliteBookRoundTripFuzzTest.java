package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.application.BookAdministrationService;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostEntryResult.Committed;
import dev.erst.fingrind.application.PostEntryResult.Rejected;
import dev.erst.fingrind.application.PostingApplicationService;
import dev.erst.fingrind.application.PostingFact;
import dev.erst.fingrind.application.PostingRejection;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
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
        BookAdministrationService administrationService =
            CliFuzzSupport.administrationService(postingFactStore);
        PostingApplicationService applicationService =
            new PostingApplicationService(
                postingFactStore,
                CliFuzzSupport.postingIdGenerator(input),
                CliFuzzSupport.fixedClock());

        assertRejected(applicationService.commit(command), PostingRejection.BookNotInitialized.class);

        CliFuzzSupport.openBook(administrationService);

        assertRejected(applicationService.commit(command), PostingRejection.UnknownAccount.class);

        var declaredAccounts = CliFuzzSupport.declarePostingAccounts(administrationService, command);
        if (CliFuzzSupport.listAccounts(administrationService).size() != declaredAccounts.size()) {
          throw new IllegalStateException("Declared-account listing drifted from setup declarations.");
        }
        DeclaredAccount primaryAccount = declaredAccounts.getFirst();
        if (!(postingFactStore.findAccount(primaryAccount.accountCode()).orElseThrow().active())) {
          throw new IllegalStateException("Primary account should be active immediately after declaration.");
        }
        deactivateAccount(bookPath, primaryAccount.accountCode().value());

        assertRejected(applicationService.commit(command), PostingRejection.InactiveAccount.class);

        CliFuzzSupport.reactivateAccount(administrationService, primaryAccount);
        if (!(postingFactStore.findAccount(primaryAccount.accountCode()).orElseThrow().active())) {
          throw new IllegalStateException("Account reactivation did not persist to SQLite.");
        }

        PostEntryResult committedResult = applicationService.commit(command);
        if (committedResult instanceof Committed committed) {
          SqliteFuzzAssertions.assertCommittedBookUsesStrictTables(bookPath);
          try (SqlitePostingFactStore reloadedStore = new SqlitePostingFactStore(bookPath)) {
            Optional<PostingFact> storedPosting =
                reloadedStore.findByIdempotency(command.requestProvenance().idempotencyKey());
            SqliteFuzzAssertions.assertStoreConnectionHardening(reloadedStore);
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

  private static void assertRejected(
      PostEntryResult result, Class<? extends PostingRejection> rejectionType) {
    if (!(result instanceof Rejected rejected)) {
      throw new IllegalStateException("Expected deterministic rejection during SQLite lifecycle setup.");
    }
    if (!rejectionType.isInstance(rejected.rejection())) {
      throw new IllegalStateException(
          "SQLite lifecycle setup returned the wrong rejection type: " + rejected.rejection());
    }
  }

  private static void deactivateAccount(Path bookPath, String accountCode) {
    try {
      SqliteFuzzAssertions.updateAccountActiveFlag(bookPath, accountCode, false);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to deactivate SQLite account during fuzz setup.", exception);
    }
  }
}
