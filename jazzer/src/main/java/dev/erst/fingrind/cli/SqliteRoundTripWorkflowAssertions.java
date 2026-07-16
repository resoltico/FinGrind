package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.jazzer.support.PostingLifecycleStatusMapper;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import dev.erst.fingrind.sqlite.SqlitePostingSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Coordinates shared SQLite round-trip execution for Jazzer fuzzing and replay. */
public final class SqliteRoundTripWorkflowAssertions {
  private SqliteRoundTripWorkflowAssertions() {}

  /** Stable SQLite round-trip snapshot consumed by fuzz harnesses and replay classifiers. */
  public record SqliteRoundTripWorkflowSnapshot(
      PostingLifecycleStatus uninitializedCommitStatus,
      PostingLifecycleStatus undeclaredCommitStatus,
      PostingLifecycleStatus inactiveCommitStatus,
      PostingLifecycleStatus finalCommitStatus,
      PostingLifecycleStatus reloadStatus,
      PostingLifecycleStatus duplicateStatus,
      boolean storedFactPresent) {
    public SqliteRoundTripWorkflowSnapshot {
      Objects.requireNonNull(uninitializedCommitStatus, "uninitializedCommitStatus");
      Objects.requireNonNull(undeclaredCommitStatus, "undeclaredCommitStatus");
      Objects.requireNonNull(inactiveCommitStatus, "inactiveCommitStatus");
      Objects.requireNonNull(finalCommitStatus, "finalCommitStatus");
      Objects.requireNonNull(reloadStatus, "reloadStatus");
      Objects.requireNonNull(duplicateStatus, "duplicateStatus");
    }
  }

  private record DirectRoundTripState(
      SqliteRoundTripWorkflowSnapshot snapshot,
      Optional<dev.erst.fingrind.core.PostingId> committedPostingId) {
    private DirectRoundTripState {
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(committedPostingId, "committedPostingId");
    }
  }

  /** Exercises one parsed posting command across direct-store and CLI workflow SQLite surfaces. */
  public static SqliteRoundTripWorkflowSnapshot exerciseRoundTripWorkflow(
      PostEntryCommand command, byte[] input) throws IOException {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(input, "input must not be null");
    Path scratchRoot = Files.createTempDirectory("fingrind-jazzer-book-");
    try {
      SqliteFuzzAssertions.prepareSecureArtifactDirectory(scratchRoot);
      DirectRoundTripState primaryState =
          drivePrimaryRoundTrip(
              command, input, scratchRoot.resolve("primary").resolve("book.sqlite"));
      SqliteRoundTripWorkflowCliCoverage.exerciseCliWorkflowCoverage(
          command, scratchRoot.resolve("workflow"));
      SqliteRoundTripWorkflowConcurrencyCoverage.exerciseConcurrentWriterCoverage(
          command, scratchRoot.resolve("concurrent"));
      SqliteRoundTripWorkflowInvalidExistingBookCoverage.exerciseInvalidExistingBookCoverage(
          command, scratchRoot.resolve("invalid-existing"));
      SqliteProtectedBookMaintenanceFuzzAssertions.exercise(
          input, scratchRoot.resolve("maintenance"));
      return primaryState.snapshot();
    } finally {
      SqliteRoundTripWorkflowResources.deleteRecursively(scratchRoot);
    }
  }

  private static DirectRoundTripState drivePrimaryRoundTrip(
      PostEntryCommand command, byte[] input, Path bookPath) throws IOException {
    try (SqlitePostingSession postingFactStore = SqliteFuzzAssertions.openStore(bookPath)) {
      BookAdministrationService administrationService =
          CliFuzzWorkflowFixtures.administrationService(postingFactStore);
      PostingApplicationService applicationService =
          CliFuzzWorkflowFixtures.postingApplicationService(
              postingFactStore, postingFactStore, CliFuzzFixtures.postingIdGenerator(input));

      PostingLifecycleStatus uninitializedCommitStatus =
          PostingLifecycleStatusMapper.forRejection(
              SqliteRoundTripWorkflowDecisionAssertions.requiredCommitRejected(
                      CliFuzzWorkflowFixtures.commit(applicationService, command))
                  .rejection());

      CliFuzzWorkflowFixtures.openBook(
          administrationService, CliFuzzFixtures.journalEntry(command).currencyUnit());

      CommitRejected postOpenRejected =
          SqliteRoundTripWorkflowDecisionAssertions.requiredCommitRejected(
              CliFuzzWorkflowFixtures.commit(applicationService, command));
      PostingLifecycleStatus undeclaredCommitStatus =
          PostingLifecycleStatusMapper.forRejection(postOpenRejected.rejection());

      PostingLifecycleStatus inactiveCommitStatus = PostingLifecycleStatus.NOT_RUN;
      if (PostingWorkflowFuzzAssertions.isUnknownAccountPreDeclarationState(
          postOpenRejected.rejection())) {
        var declaredAccounts =
            CliFuzzAccountFixtures.declarePostingAccounts(administrationService, command);
        var listedAccounts = CliFuzzAccountFixtures.listAccounts(postingFactStore);
        SqliteRoundTripWorkflowPersistenceAssertions.verifyDeclaredAccountListing(
            listedAccounts, declaredAccounts);
        DeclaredAccount primaryAccount = declaredAccounts.getFirst();
        SqliteFuzzAssertions.deactivateAccount(bookPath, primaryAccount.accountCode().value());

        inactiveCommitStatus =
            PostingLifecycleStatusMapper.forRejection(
                SqliteRoundTripWorkflowDecisionAssertions.requiredCommitRejected(
                        CliFuzzWorkflowFixtures.commit(applicationService, command))
                    .rejection());

        CliFuzzAccountFixtures.reactivateAccount(administrationService, primaryAccount);
        SqliteRoundTripWorkflowPersistenceAssertions.assertAccountReactivationPersisted(
            postingFactStore, primaryAccount.accountCode());
      }

      CommitEntryResult committedResult =
          CliFuzzWorkflowFixtures.commit(applicationService, command);
      return switch (committedResult) {
        case Committed committed ->
            committedState(
                command,
                input,
                bookPath,
                committed,
                uninitializedCommitStatus,
                undeclaredCommitStatus,
                inactiveCommitStatus);
        case CommitRejected rejected ->
            rejectedState(
                command,
                applicationService,
                postingFactStore,
                rejected,
                uninitializedCommitStatus,
                undeclaredCommitStatus,
                inactiveCommitStatus);
      };
    }
  }

  private static DirectRoundTripState committedState(
      PostEntryCommand command,
      byte[] input,
      Path bookPath,
      Committed committed,
      PostingLifecycleStatus uninitializedCommitStatus,
      PostingLifecycleStatus undeclaredCommitStatus,
      PostingLifecycleStatus inactiveCommitStatus)
      throws IOException {
    SqliteFuzzAssertions.assertCommittedBookUsesStrictTables(bookPath);
    try (SqlitePostingSession reloadedStore = SqliteFuzzAssertions.openStore(bookPath)) {
      PostingFact postingFact =
          SqliteRoundTripWorkflowPersistenceAssertions.requireStoredPosting(
              CliFuzzWorkflowFixtures.publishedStoredPosting(
                  reloadedStore, command.requestProvenance().idempotencyKey()));
      SqliteFuzzAssertions.assertStoreConnectionHardening(reloadedStore);
      SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
          postingFact, committed, command);

      PostingApplicationService duplicateService =
          CliFuzzWorkflowFixtures.postingApplicationService(
              reloadedStore, reloadedStore, CliFuzzFixtures.postingIdGenerator(input));
      PostingLifecycleStatus duplicateStatus =
          SqliteRoundTripWorkflowPersistenceAssertions.requireIdempotentReplay(
              CliFuzzWorkflowFixtures.commit(duplicateService, command), committed);
      return new DirectRoundTripState(
          new SqliteRoundTripWorkflowSnapshot(
              uninitializedCommitStatus,
              undeclaredCommitStatus,
              inactiveCommitStatus,
              PostingLifecycleStatus.COMMITTED,
              PostingLifecycleStatus.RELOADED,
              duplicateStatus,
              true),
          Optional.of(committed.postingId()));
    }
  }

  private static DirectRoundTripState rejectedState(
      PostEntryCommand command,
      PostingApplicationService applicationService,
      SqlitePostingSession postingFactStore,
      CommitRejected rejected,
      PostingLifecycleStatus uninitializedCommitStatus,
      PostingLifecycleStatus undeclaredCommitStatus,
      PostingLifecycleStatus inactiveCommitStatus) {
    SqliteRoundTripWorkflowPersistenceAssertions.assertRejectedStateDidNotPersistPosting(
        CliFuzzWorkflowFixtures.publishedStoredPosting(
            postingFactStore, command.requestProvenance().idempotencyKey()));
    PostingLifecycleStatus duplicateStatus =
        SqliteRoundTripWorkflowPersistenceAssertions.verifyRejectedCommitConsistency(
            rejected, CliFuzzWorkflowFixtures.commit(applicationService, command));
    return new DirectRoundTripState(
        new SqliteRoundTripWorkflowSnapshot(
            uninitializedCommitStatus,
            undeclaredCommitStatus,
            inactiveCommitStatus,
            PostingLifecycleStatusMapper.forRejection(rejected.rejection()),
            PostingLifecycleStatus.NOT_RUN,
            duplicateStatus,
            false),
        Optional.empty());
  }
}
