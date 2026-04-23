package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import org.junit.jupiter.api.Test;

/** Covers deterministic direct replay for posting-workflow seeds. */
class JazzerReplayPostingWorkflowTest {
  @Test
  void replay_returnsSuccessForValidPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new PostingWorkflowReplayDetails(
            "PARSED",
            "2026-04-07",
            "idem-1",
            2,
            false,
            "REJECTED_BOOK_NOT_INITIALIZED",
            "REJECTED_BOOK_NOT_INITIALIZED",
            "REJECTED_UNKNOWN_ACCOUNT",
            "REJECTED_UNKNOWN_ACCOUNT",
            "REJECTED_INACTIVE_ACCOUNT",
            "REJECTED_INACTIVE_ACCOUNT",
            "PREFLIGHT_ACCEPTED",
            "COMMITTED",
            "REJECTED_DUPLICATE_IDEMPOTENCY_KEY",
            true,
            "NONE"),
        success.details());
  }

  @Test
  void replay_returnsSuccessForReversalTargetMissingPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            JazzerReplayRequestFixtures.reversalTargetMissingRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new PostingWorkflowReplayDetails(
            "PARSED",
            "2026-04-08",
            "idem-5",
            2,
            true,
            "REJECTED_BOOK_NOT_INITIALIZED",
            "REJECTED_BOOK_NOT_INITIALIZED",
            "REJECTED_UNKNOWN_ACCOUNT",
            "REJECTED_UNKNOWN_ACCOUNT",
            "REJECTED_INACTIVE_ACCOUNT",
            "REJECTED_INACTIVE_ACCOUNT",
            "REJECTED_REVERSAL_TARGET_NOT_FOUND",
            "REJECTED_REVERSAL_TARGET_NOT_FOUND",
            "NOT_RUN",
            false,
            "NONE"),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForMissingReversalReasonPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            JazzerReplayRequestFixtures.missingReversalReasonRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new PostingWorkflowReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            false,
            "Missing required field: reason"),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            JazzerReplayRequestFixtures.invalidBlankActorRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new PostingWorkflowReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            false,
            "Actor id must not be blank."),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForExponentAmountPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            JazzerReplayRequestFixtures.invalidExponentAmountRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new PostingWorkflowReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            "NOT_RUN",
            false,
            "Money amount must be a plain decimal string without exponent notation."),
        invalid.details());
  }
}
