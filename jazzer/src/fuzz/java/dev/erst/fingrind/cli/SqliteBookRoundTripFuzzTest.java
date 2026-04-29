package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.sqlite.SqliteBookSession;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
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
      PostEntryCommand command = CliFuzzFixtures.readPostEntryCommand(input);
      roundTripParsedCommand(command, input);
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }

  private static void roundTripParsedCommand(PostEntryCommand command, byte[] input)
      throws IOException {
    Path bookPath =
        Files.createTempDirectory("fingrind-jazzer-book-")
            .resolve("arbitrary")
            .resolve("entity-book.sqlite");

    try (SqliteBookSession postingFactStore = SqliteFuzzAssertions.openStore(bookPath)) {
      BookAdministrationService administrationService =
          CliFuzzFixtures.administrationService(postingFactStore.administrationSession());
      PostingApplicationService applicationService =
          new PostingApplicationService(
              postingFactStore.postingSession(),
              CliFuzzFixtures.postingIdGenerator(input),
              CliFuzzFixtures.fixedClock());

      driveSqliteLifecycleToReadyBook(
          command, bookPath, postingFactStore, administrationService, applicationService);

      CommitEntryResult committedResult = applicationService.commit(command);
      if (committedResult instanceof Committed committed) {
        assertCommittedRoundTrip(command, input, bookPath, committed);
      } else if (committedResult instanceof CommitRejected rejected) {
        assertRejectedRoundTrip(command, postingFactStore, applicationService, rejected);
      } else {
        throw new IllegalStateException("Unexpected SQLite commit result type.");
      }
    }
  }

  private static void driveSqliteLifecycleToReadyBook(
      PostEntryCommand command,
      Path bookPath,
      SqliteBookSession postingFactStore,
      BookAdministrationService administrationService,
      PostingApplicationService applicationService) {
    assertRejected(applicationService.commit(command), PostingRejection.BookNotInitialized.class);

    CliFuzzFixtures.openBook(administrationService);

    assertAccountStateRejected(
        applicationService.commit(command), PostingRejection.UnknownAccount.class);

    DeclaredAccount primaryAccount =
        declareAndDeactivatePrimaryAccount(
            command, bookPath, postingFactStore, administrationService);

    assertAccountStateRejected(
        applicationService.commit(command), PostingRejection.InactiveAccount.class);

    CliFuzzFixtures.reactivateAccount(administrationService, primaryAccount);
    if (!postingFactStore.findAccount(primaryAccount.accountCode()).orElseThrow().active()) {
      throw new IllegalStateException("Account reactivation did not persist to SQLite.");
    }
  }

  private static DeclaredAccount declareAndDeactivatePrimaryAccount(
      PostEntryCommand command,
      Path bookPath,
      SqliteBookSession postingFactStore,
      BookAdministrationService administrationService) {
    var declaredAccounts = CliFuzzFixtures.declarePostingAccounts(administrationService, command);
    if (CliFuzzFixtures.listAccounts(postingFactStore.readSession()).size()
        != declaredAccounts.size()) {
      throw new IllegalStateException("Declared-account listing drifted from setup declarations.");
    }
    DeclaredAccount primaryAccount = declaredAccounts.getFirst();
    if (!postingFactStore.findAccount(primaryAccount.accountCode()).orElseThrow().active()) {
      throw new IllegalStateException(
          "Primary account should be active immediately after declaration.");
    }
    deactivateAccount(bookPath, primaryAccount.accountCode().value());
    return primaryAccount;
  }

  private static void assertCommittedRoundTrip(
      PostEntryCommand command, byte[] input, Path bookPath, Committed committed)
      throws IOException {
    SqliteFuzzAssertions.assertCommittedBookUsesStrictTables(bookPath);
    try (SqliteBookSession reloadedStore = SqliteFuzzAssertions.openStore(bookPath)) {
      PostingFact postingFact = loadReloadedPosting(command, reloadedStore);
      SqliteFuzzAssertions.assertStoreConnectionHardening(reloadedStore);
      assertReloadedPostingMatches(command, committed, postingFact);

      PostingApplicationService duplicateService =
          new PostingApplicationService(
              reloadedStore.postingSession(),
              CliFuzzFixtures.postingIdGenerator(input),
              CliFuzzFixtures.fixedClock());
      CommitEntryResult duplicateResult = duplicateService.commit(command);
      if (!(duplicateResult instanceof CommitRejected rejected)) {
        throw new IllegalStateException("Duplicate SQLite commit should be rejected.");
      }
      if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
        throw new IllegalStateException(
            "Duplicate SQLite commit returned the wrong rejection code.");
      }
    }
  }

  private static PostingFact loadReloadedPosting(
      PostEntryCommand command, SqliteBookSession reloadedStore) {
    Optional<PostingFact> storedPosting =
        reloadedStore.findExistingPosting(command.requestProvenance().idempotencyKey());
    if (storedPosting.isEmpty()) {
      throw new IllegalStateException("Committed posting fact was not persisted to SQLite.");
    }
    return storedPosting.orElseThrow();
  }

  private static void assertReloadedPostingMatches(
      PostEntryCommand command, Committed committed, PostingFact postingFact) {
    if (!postingFact.postingId().equals(committed.postingId())) {
      throw new IllegalStateException("Reloaded posting id differs from the commit result.");
    }
    if (!postingFact.journalEntry().equals(command.journalEntry())) {
      throw new IllegalStateException("Reloaded journal entry differs from the parsed command.");
    }
    if (!postingFact.reversalReference().equals(command.reversalReference())) {
      throw new IllegalStateException("Reloaded reversal differs from the parsed command.");
    }
    if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
      throw new IllegalStateException(
          "Reloaded request provenance differs from the parsed command.");
    }
    if (!postingFact.provenance().recordedAt().equals(CliFuzzFixtures.fixedClock().instant())) {
      throw new IllegalStateException("Reloaded recorded-at differs from the deterministic clock.");
    }
    if (!postingFact.provenance().sourceChannel().equals(command.sourceChannel())) {
      throw new IllegalStateException("Reloaded source channel differs from the parsed command.");
    }
  }

  private static void assertRejectedRoundTrip(
      PostEntryCommand command,
      SqliteBookSession postingFactStore,
      PostingApplicationService applicationService,
      CommitRejected rejected) {
    if (postingFactStore
        .findExistingPosting(command.requestProvenance().idempotencyKey())
        .isPresent()) {
      throw new IllegalStateException("Rejected SQLite command must not persist a posting fact.");
    }
    CommitEntryResult repeatedResult = applicationService.commit(command);
    if (!(repeatedResult instanceof CommitRejected repeatedRejected)) {
      throw new IllegalStateException("Rejected SQLite command should remain rejected.");
    }
    if (!repeatedRejected.rejection().equals(rejected.rejection())) {
      throw new IllegalStateException("Repeated SQLite rejection changed unexpectedly.");
    }
  }

  private static void assertRejected(
      CommitEntryResult result, Class<? extends PostingRejection> rejectionType) {
    if (!(result instanceof CommitRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic rejection during SQLite lifecycle setup.");
    }
    if (!rejectionType.isInstance(rejected.rejection())) {
      throw new IllegalStateException(
          "SQLite lifecycle setup returned the wrong rejection type: " + rejected.rejection());
    }
  }

  private static void assertAccountStateRejected(
      CommitEntryResult result,
      Class<? extends PostingRejection.AccountStateViolation> violationType) {
    if (!(result instanceof CommitRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic rejection during SQLite lifecycle setup.");
    }
    if (!(rejected.rejection() instanceof PostingRejection.AccountStateViolations violations)) {
      throw new IllegalStateException(
          "Expected account-state violations during SQLite lifecycle setup but got: "
              + rejected.rejection());
    }
    if (violations.violations().isEmpty()
        || violations.violations().stream()
            .anyMatch(violation -> !violationType.isInstance(violation))) {
      throw new IllegalStateException(
          "SQLite lifecycle setup returned the wrong account-state violations: "
              + violations.violations());
    }
  }

  private static void deactivateAccount(Path bookPath, String accountCode) {
    try {
      SqliteFuzzAssertions.deactivateAccount(bookPath, accountCode);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to deactivate SQLite account during fuzz setup.", exception);
    }
  }
}
