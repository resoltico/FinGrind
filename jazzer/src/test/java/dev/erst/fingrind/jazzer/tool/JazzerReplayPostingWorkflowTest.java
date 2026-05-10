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
            new ParsedPostingCommandDetails("2026-04-07", "idem-1", 2, false),
            new PostingWorkflowLifecycleDetails(
                new PostingGateDetails(
                    PostingLifecycleStatus.BOOK_NOT_INITIALIZED,
                    PostingLifecycleStatus.BOOK_NOT_INITIALIZED),
                new PostingGateDetails(
                    PostingLifecycleStatus.UNKNOWN_ACCOUNT, PostingLifecycleStatus.UNKNOWN_ACCOUNT),
                new PostingGateDetails(
                    PostingLifecycleStatus.INACTIVE_ACCOUNT,
                    PostingLifecycleStatus.INACTIVE_ACCOUNT)),
            new PostingWorkflowOutcomeDetails(
                PostingLifecycleStatus.PREFLIGHT_ACCEPTED,
                PostingLifecycleStatus.COMMITTED,
                PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
                true)),
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
            new ParsedPostingCommandDetails("2026-04-08", "idem-5", 2, true),
            new PostingWorkflowLifecycleDetails(
                new PostingGateDetails(
                    PostingLifecycleStatus.BOOK_NOT_INITIALIZED,
                    PostingLifecycleStatus.BOOK_NOT_INITIALIZED),
                new PostingGateDetails(
                    PostingLifecycleStatus.UNKNOWN_ACCOUNT, PostingLifecycleStatus.UNKNOWN_ACCOUNT),
                new PostingGateDetails(
                    PostingLifecycleStatus.INACTIVE_ACCOUNT,
                    PostingLifecycleStatus.INACTIVE_ACCOUNT)),
            new PostingWorkflowOutcomeDetails(
                PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
                PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
                PostingLifecycleStatus.NOT_RUN,
                false)),
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
    assertEquals(new UnparsedPostingWorkflowReplayDetails(), invalid.details());
    assertEquals("Missing required field: reason", invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            JazzerReplayRequestFixtures.invalidBlankActorRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedPostingWorkflowReplayDetails(), invalid.details());
    assertEquals("Actor id must not be blank.", invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForExponentAmountPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            JazzerReplayRequestFixtures.invalidExponentAmountRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedPostingWorkflowReplayDetails(), invalid.details());
    assertEquals("minorUnits must contain ASCII decimal digits only.", invalid.message());
  }
}
