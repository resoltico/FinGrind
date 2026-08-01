package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import org.junit.jupiter.api.Test;

class SqliteRoundTripWorkflowExerciseTest {
  @Test
  void roundTripWorkflow_preserves_workspace_evidence_for_io_runtime_and_fatal_failures()
      throws Exception {
    PostEntryCommand command = SqliteRoundTripWorkflowTestSupport.basicValidCommand();
    byte[] input = "workflow-failure".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    java.io.IOException ioFailure =
        assertThrows(
            java.io.IOException.class,
            () ->
                SqliteRoundTripWorkflowAssertions.exerciseRoundTripWorkflow(
                    command,
                    input,
                    (_command, _input, _scratchRoot) -> {
                      throw new java.io.IOException("simulated workflow io failure");
                    }));
    assertEquals(1, ioFailure.getSuppressed().length);

    IllegalStateException runtimeFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowAssertions.exerciseRoundTripWorkflow(
                    command,
                    input,
                    (_command, _input, _scratchRoot) -> {
                      throw new IllegalStateException("simulated workflow runtime failure");
                    }));
    assertEquals(1, runtimeFailure.getSuppressed().length);

    AssertionError fatalFailure =
        assertThrows(
            AssertionError.class,
            () ->
                SqliteRoundTripWorkflowAssertions.exerciseRoundTripWorkflow(
                    command,
                    input,
                    (_command, _input, _scratchRoot) -> {
                      throw new AssertionError("simulated workflow fatal failure");
                    }));
    assertEquals(1, fatalFailure.getSuppressed().length);
  }

  @Test
  void roundTripWorkflow_covers_rejected_direct_commit_snapshots() throws Exception {
    PostEntryCommand baseCommand = SqliteRoundTripWorkflowTestSupport.basicValidCommand();
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.Reversal(
                CliFuzzFixtures.journalEntry(baseCommand).effectiveDate(),
                new PostingLineage.Reversal(
                    new ReversalReference(new PostingId("35b64143-46df-384f-898b-57d9ce1c50c1")),
                    new ReversalReason("Missing prior posting")),
                null,
                CliFuzzFixtures.journalEntry(baseCommand)),
            baseCommand.evidence(),
            baseCommand.requestProvenance(),
            baseCommand.sourceChannel());

    var snapshot =
        SqliteRoundTripWorkflowAssertions.exerciseRoundTripWorkflow(
            command, "reversal-target-not-found".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertEquals(PostingLifecycleStatus.BOOK_NOT_INITIALIZED, snapshot.uninitializedCommitStatus());
    assertEquals(PostingLifecycleStatus.UNKNOWN_ACCOUNT, snapshot.undeclaredCommitStatus());
    assertEquals(PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND, snapshot.inactiveCommitStatus());
    assertEquals(PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND, snapshot.finalCommitStatus());
    assertEquals(PostingLifecycleStatus.NOT_RUN, snapshot.reloadStatus());
    assertEquals(PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND, snapshot.duplicateStatus());
    assertFalse(snapshot.storedFactPresent());
  }
}
