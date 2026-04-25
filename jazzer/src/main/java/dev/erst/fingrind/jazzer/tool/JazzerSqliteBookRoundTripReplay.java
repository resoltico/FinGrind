package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.sqlite.SqliteBookSession;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Replays posting-command workflows against the SQLite-backed round-trip harness. */
final class JazzerSqliteBookRoundTripReplay {
  private JazzerSqliteBookRoundTripReplay() {}

  static ReplayOutcome replay(byte[] input) {
    return replay(
        input,
        CliFuzzFixtures::readPostEntryCommand,
        JazzerSqliteBookRoundTripReplay::exerciseRoundTripWorkflow);
  }

  static ReplayOutcome replay(
      byte[] input, PostEntryCommandParser parser, SqliteRoundTripExercise roundTripExercise) {
    PostEntryCommand command = null;
    SqliteRoundTripReplayState state = new SqliteRoundTripReplayState();
    try {
      command = parser.parse(input);
      roundTripExercise.exercise(command, input, state);
      return new ReplayOutcome.Success(
          JazzerHarness.sqliteBookRoundTrip().key(), state.details(command));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.sqliteBookRoundTrip().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayDetailsMapper.normalizedMessage(expected),
          JazzerReplayDetailsMapper.unparsedSqliteBookRoundTripDetails());
    } catch (IOException | RuntimeException unexpected) {
      return JazzerReplayDetailsMapper.unexpectedFailure(
          JazzerHarness.sqliteBookRoundTrip(),
          unexpected,
          command == null
              ? JazzerReplayDetailsMapper.unparsedSqliteBookRoundTripDetails()
              : state.details(command));
    }
  }

  private static void exerciseRoundTripWorkflow(
      PostEntryCommand command, byte[] input, SqliteRoundTripReplayState state) throws IOException {
    try (JazzerReplayScratchDirectory scratchDirectory =
        JazzerReplayScratchDirectory.create("fingrind-jazzer-replay-")) {
      Path bookPath = scratchDirectory.resolve(Path.of("nested", "entity-book.sqlite"));

      try (SqliteBookSession postingFactStore = SqliteFuzzAssertions.openStore(bookPath)) {
        BookAdministrationService administrationService =
            CliFuzzFixtures.administrationService(postingFactStore.administrationSession());
        PostingApplicationService applicationService =
            new PostingApplicationService(
                postingFactStore.postingSession(),
                CliFuzzFixtures.postingIdGenerator(input),
                CliFuzzFixtures.fixedClock());

        state.uninitializedCommitStatus =
            JazzerReplayDetailsMapper.rejectionStatus(
                JazzerReplayDetailsMapper.requiredCommitRejected(applicationService.commit(command))
                    .rejection());

        CliFuzzFixtures.openBook(administrationService);

        state.undeclaredCommitStatus =
            JazzerReplayDetailsMapper.rejectionStatus(
                JazzerReplayDetailsMapper.requiredCommitRejected(applicationService.commit(command))
                    .rejection());

        List<DeclaredAccount> declaredAccounts =
            CliFuzzFixtures.declarePostingAccounts(administrationService, command);
        SqliteRoundTripReplayVerifier.verifyDeclaredAccountListing(
            CliFuzzFixtures.listAccounts(postingFactStore.readSession()).size(),
            declaredAccounts.size());
        DeclaredAccount primaryAccount = declaredAccounts.getFirst();
        SqliteFuzzAssertions.deactivateAccount(bookPath, primaryAccount.accountCode().value());
        state.inactiveCommitStatus =
            JazzerReplayDetailsMapper.rejectionStatus(
                JazzerReplayDetailsMapper.requiredCommitRejected(applicationService.commit(command))
                    .rejection());

        CliFuzzFixtures.reactivateAccount(administrationService, primaryAccount);

        CommitEntryResult committedResult = applicationService.commit(command);
        switch (committedResult) {
          case Committed committed -> {
            state.finalCommitStatus = PostingLifecycleStatus.COMMITTED;
            try (SqliteBookSession reloadedStore = SqliteFuzzAssertions.openStore(bookPath)) {
              PostingFact postingFact =
                  SqliteRoundTripReplayVerifier.requireStoredPosting(
                      reloadedStore.findExistingPosting(
                          command.requestProvenance().idempotencyKey()));
              SqliteRoundTripReplayVerifier.verifyReloadedPosting(postingFact, committed, command);
              state.storedFactPresent = true;
              state.reloadStatus = PostingLifecycleStatus.RELOADED;

              PostingApplicationService duplicateService =
                  new PostingApplicationService(
                      reloadedStore.postingSession(),
                      CliFuzzFixtures.postingIdGenerator(input),
                      CliFuzzFixtures.fixedClock());
              state.duplicateStatus =
                  SqliteRoundTripReplayVerifier.requireDuplicateRejection(
                      duplicateService.commit(command));
            }
          }
          case CommitRejected rejected -> {
            state.finalCommitStatus =
                JazzerReplayDetailsMapper.rejectionStatus(rejected.rejection());
            state.duplicateStatus =
                SqliteRoundTripReplayVerifier.verifyRejectedCommitConsistency(
                    rejected, applicationService.commit(command));
          }
        }
      }
    }
  }

  /** Parses one SQLite round-trip replay input into the production posting command model. */
  @FunctionalInterface
  interface PostEntryCommandParser {
    /** Parses one raw replay payload into a posting command. */
    PostEntryCommand parse(byte[] input);
  }

  /** Exercises one parsed posting command against the SQLite-backed replay workflow. */
  @FunctionalInterface
  interface SqliteRoundTripExercise {
    /** Applies one parsed command to the SQLite replay workflow and records the outcome state. */
    void exercise(PostEntryCommand command, byte[] input, SqliteRoundTripReplayState state)
        throws IOException;
  }

  /** Collects lifecycle checkpoints and the final outcome for one SQLite round-trip replay. */
  static final class SqliteRoundTripReplayState {
    private PostingLifecycleStatus uninitializedCommitStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus undeclaredCommitStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus inactiveCommitStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus finalCommitStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus reloadStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus duplicateStatus = PostingLifecycleStatus.NOT_RUN;
    private boolean storedFactPresent;

    private SqliteBookRoundTripReplayDetails details(PostEntryCommand command) {
      return JazzerReplayDetailsMapper.sqliteBookRoundTripDetails(
          command, lifecycleDetails(), outcomeDetails());
    }

    private SqliteBookRoundTripLifecycleDetails lifecycleDetails() {
      return new SqliteBookRoundTripLifecycleDetails(
          uninitializedCommitStatus, undeclaredCommitStatus, inactiveCommitStatus);
    }

    private SqliteBookRoundTripOutcomeDetails outcomeDetails() {
      return new SqliteBookRoundTripOutcomeDetails(
          finalCommitStatus, reloadStatus, duplicateStatus, storedFactPresent);
    }
  }
}
