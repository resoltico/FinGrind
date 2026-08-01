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
            CommittedRegressionSeedFixtures.postingWorkflow("basic_valid.json").getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new PostingWorkflowReplayDetails(
            new ParsedPostingCommandDetails("2026-04-07", "idem-posting-4", 2, false),
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
                PostingLifecycleStatus.IDEMPOTENT_REPLAY,
                true)),
        success.details());
  }

  @Test
  void replay_returnsSuccessForReversalTargetMissingPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            CommittedRegressionSeedFixtures.postingWorkflow("reversal_target_missing.json")
                .getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new PostingWorkflowReplayDetails(
            new ParsedPostingCommandDetails("2026-04-07", "idem-5", 2, true),
            new PostingWorkflowLifecycleDetails(
                new PostingGateDetails(
                    PostingLifecycleStatus.BOOK_NOT_INITIALIZED,
                    PostingLifecycleStatus.BOOK_NOT_INITIALIZED),
                new PostingGateDetails(
                    PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
                    PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND),
                new PostingGateDetails(
                    PostingLifecycleStatus.NOT_RUN, PostingLifecycleStatus.NOT_RUN)),
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
            CommittedRegressionSeedFixtures.postingWorkflow("invalid_missing_reversal_reason.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedPostingWorkflowReplayDetails(), invalid.details());
    assertEquals(
        CommittedRegressionSeedFixtures.expectation(
                JazzerHarness.postingWorkflow(), "invalid_missing_reversal_reason.json")
            .message(),
        invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForBlankCommandIdPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            CommittedRegressionSeedFixtures.postingWorkflow("invalid_blank_command_id.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedPostingWorkflowReplayDetails(), invalid.details());
    assertEquals(
        CommittedRegressionSeedFixtures.expectation(
                JazzerHarness.postingWorkflow(), "invalid_blank_command_id.json")
            .message(),
        invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForExponentAmountPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.postingWorkflow(),
            CommittedRegressionSeedFixtures.postingWorkflow("invalid_amount_exponent.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedPostingWorkflowReplayDetails(), invalid.details());
    assertEquals(
        CommittedRegressionSeedFixtures.expectation(
                JazzerHarness.postingWorkflow(), "invalid_amount_exponent.json")
            .message(),
        invalid.message());
  }
}
