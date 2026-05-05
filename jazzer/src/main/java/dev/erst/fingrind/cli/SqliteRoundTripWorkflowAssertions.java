package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import dev.erst.fingrind.sqlite.SqliteBookSession;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
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
      DirectRoundTripState primaryState =
          drivePrimaryRoundTrip(
              command, input, scratchRoot.resolve("primary").resolve("book.sqlite"));
      SqliteRoundTripWorkflowCliCoverage.exerciseCliWorkflowCoverage(
          command, scratchRoot.resolve("workflow"));
      SqliteRoundTripWorkflowConcurrencyCoverage.exerciseConcurrentWriterCoverage(
          command, scratchRoot.resolve("concurrent"));
      SqliteRoundTripWorkflowInvalidExistingBookCoverage.exerciseInvalidExistingBookCoverage(
          command, scratchRoot.resolve("invalid-existing"));
      return primaryState.snapshot();
    } finally {
      SqliteRoundTripWorkflowResources.deleteRecursively(scratchRoot);
    }
  }

  private static DirectRoundTripState drivePrimaryRoundTrip(
      PostEntryCommand command, byte[] input, Path bookPath) throws IOException {
    try (SqliteBookSession postingFactStore = SqliteFuzzAssertions.openStore(bookPath)) {
      BookAdministrationService administrationService =
          CliFuzzFixtures.administrationService(postingFactStore.administrationSession());
      PostingApplicationService applicationService =
          new PostingApplicationService(
              postingFactStore.postingSession(),
              CliFuzzFixtures.postingIdGenerator(input),
              CliFuzzFixtures.fixedClock());

      PostingLifecycleStatus uninitializedCommitStatus =
          SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
              SqliteRoundTripWorkflowLifecycleAssertions.requiredCommitRejected(
                      CliFuzzFixtures.commit(applicationService, command))
                  .rejection());

      CliFuzzFixtures.openBook(administrationService);

      PostingLifecycleStatus undeclaredCommitStatus =
          SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
              SqliteRoundTripWorkflowLifecycleAssertions.requiredCommitRejected(
                      CliFuzzFixtures.commit(applicationService, command))
                  .rejection());

      var declaredAccounts = CliFuzzFixtures.declarePostingAccounts(administrationService, command);
      SqliteRoundTripWorkflowLifecycleAssertions.verifyDeclaredAccountListing(
          CliFuzzFixtures.listAccounts(postingFactStore.readSession()).size(),
          declaredAccounts.size());
      DeclaredAccount primaryAccount = declaredAccounts.getFirst();
      SqliteFuzzAssertions.deactivateAccount(bookPath, primaryAccount.accountCode().value());

      PostingLifecycleStatus inactiveCommitStatus =
          SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
              SqliteRoundTripWorkflowLifecycleAssertions.requiredCommitRejected(
                      CliFuzzFixtures.commit(applicationService, command))
                  .rejection());

      CliFuzzFixtures.reactivateAccount(administrationService, primaryAccount);
      SqliteRoundTripWorkflowLifecycleAssertions.assertAccountReactivationPersisted(
          postingFactStore, primaryAccount.accountCode());

      CommitEntryResult committedResult = CliFuzzFixtures.commit(applicationService, command);
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
    try (SqliteBookSession reloadedStore = SqliteFuzzAssertions.openStore(bookPath)) {
      PostingFact postingFact =
          SqliteRoundTripWorkflowLifecycleAssertions.requireStoredPosting(
              CliFuzzFixtures.publishedStoredPosting(
                  reloadedStore.postingSession(), command.requestProvenance().idempotencyKey()));
      SqliteFuzzAssertions.assertStoreConnectionHardening(reloadedStore);
      SqliteRoundTripWorkflowLifecycleAssertions.verifyReloadedPosting(
          postingFact, committed, command);

      PostingApplicationService duplicateService =
          new PostingApplicationService(
              reloadedStore.postingSession(),
              CliFuzzFixtures.postingIdGenerator(input),
              CliFuzzFixtures.fixedClock());
      PostingLifecycleStatus duplicateStatus =
          SqliteRoundTripWorkflowLifecycleAssertions.requireDuplicateRejection(
              CliFuzzFixtures.commit(duplicateService, command));
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
      SqliteBookSession postingFactStore,
      CommitRejected rejected,
      PostingLifecycleStatus uninitializedCommitStatus,
      PostingLifecycleStatus undeclaredCommitStatus,
      PostingLifecycleStatus inactiveCommitStatus) {
    SqliteRoundTripWorkflowLifecycleAssertions.assertRejectedStateDidNotPersistPosting(
        CliFuzzFixtures.publishedStoredPosting(
            postingFactStore.postingSession(), command.requestProvenance().idempotencyKey()));
    PostingLifecycleStatus duplicateStatus =
        SqliteRoundTripWorkflowLifecycleAssertions.verifyRejectedCommitConsistency(
            rejected, CliFuzzFixtures.commit(applicationService, command));
    return new DirectRoundTripState(
        new SqliteRoundTripWorkflowSnapshot(
            uninitializedCommitStatus,
            undeclaredCommitStatus,
            inactiveCommitStatus,
            SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(rejected.rejection()),
            PostingLifecycleStatus.NOT_RUN,
            duplicateStatus,
            false),
        Optional.empty());
  }
}
