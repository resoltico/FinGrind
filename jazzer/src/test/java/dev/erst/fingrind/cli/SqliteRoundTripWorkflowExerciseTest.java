package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import org.junit.jupiter.api.Test;

class SqliteRoundTripWorkflowExerciseTest {
  @Test
  void roundTripWorkflow_covers_rejected_direct_commit_snapshots() throws Exception {
    PostEntryCommand baseCommand = SqliteRoundTripWorkflowTestSupport.basicValidCommand();
    PostEntryCommand command =
        new PostEntryCommand(
            baseCommand.postingKind(),
            baseCommand.journalEntry(),
            PostingLineage.reversal(
                new ReversalReference(new PostingId("missing-posting")),
                new ReversalReason("Missing prior posting")),
            baseCommand.evidence(),
            baseCommand.requestProvenance(),
            baseCommand.sourceChannel());

    var snapshot =
        SqliteRoundTripWorkflowAssertions.exerciseRoundTripWorkflow(
            command, "reversal-target-not-found".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertEquals(PostingLifecycleStatus.BOOK_NOT_INITIALIZED, snapshot.uninitializedCommitStatus());
    assertEquals(PostingLifecycleStatus.UNKNOWN_ACCOUNT, snapshot.undeclaredCommitStatus());
    assertEquals(PostingLifecycleStatus.INACTIVE_ACCOUNT, snapshot.inactiveCommitStatus());
    assertEquals(PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND, snapshot.finalCommitStatus());
    assertEquals(PostingLifecycleStatus.NOT_RUN, snapshot.reloadStatus());
    assertEquals(PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND, snapshot.duplicateStatus());
    assertFalse(snapshot.storedFactPresent());
  }
}
