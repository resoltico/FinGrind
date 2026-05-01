package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.cli.SqliteRoundTripWorkflowAssertions;
import dev.erst.fingrind.cli.SqliteRoundTripWorkflowAssertions.SqliteRoundTripWorkflowSnapshot;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;

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
    SqliteRoundTripWorkflowSnapshot snapshot =
        SqliteRoundTripWorkflowAssertions.exerciseRoundTripWorkflow(command, input);
    state.uninitializedCommitStatus = snapshot.uninitializedCommitStatus();
    state.undeclaredCommitStatus = snapshot.undeclaredCommitStatus();
    state.inactiveCommitStatus = snapshot.inactiveCommitStatus();
    state.finalCommitStatus = snapshot.finalCommitStatus();
    state.reloadStatus = snapshot.reloadStatus();
    state.duplicateStatus = snapshot.duplicateStatus();
    state.storedFactPresent = snapshot.storedFactPresent();
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
