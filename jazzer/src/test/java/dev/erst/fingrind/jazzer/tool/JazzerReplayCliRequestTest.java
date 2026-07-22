package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import org.junit.jupiter.api.Test;

/** Covers deterministic direct replay for CLI request seeds. */
class JazzerReplayCliRequestTest {
  @Test
  void replay_returnsSuccessForValidCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            CommittedRegressionSeedFixtures.cliRequest("basic_valid.json").getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            new ParsedPostingCommandDetails("2026-04-07", "idem-1", 2, false), SourceChannel.CLI),
        success.details());
    assertEquals(ReplayOutcomeKind.SUCCESS, success.kind());
    assertEquals(ReplayOutcome.SUCCESS_MESSAGE, success.message());
  }

  @Test
  void replay_returnsExpectedInvalidForForbiddenRecordedAtCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            CommittedRegressionSeedFixtures.cliRequest("invalid_forbidden_recorded_at.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedCliRequestReplayDetails(), invalid.details());
    assertEquals(ReplayOutcomeKind.EXPECTED_INVALID, invalid.kind());
    assertEquals(
        CommittedRegressionSeedFixtures.expectation(
                JazzerHarness.cliRequest(), "invalid_forbidden_recorded_at.json")
            .message(),
        invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForForbiddenSourceChannelCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            CommittedRegressionSeedFixtures.cliRequest("invalid_forbidden_source_channel.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedCliRequestReplayDetails(), invalid.details());
    assertEquals(
        CommittedRegressionSeedFixtures.expectation(
                JazzerHarness.cliRequest(), "invalid_forbidden_source_channel.json")
            .message(),
        invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            CommittedRegressionSeedFixtures.cliRequest("invalid_missing_provenance.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedCliRequestReplayDetails(), invalid.details());
    assertEquals(
        CommittedRegressionSeedFixtures.expectation(
                JazzerHarness.cliRequest(), "invalid_missing_provenance.json")
            .message(),
        invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForExponentAmountCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            CommittedRegressionSeedFixtures.cliRequest("invalid_amount_exponent.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedCliRequestReplayDetails(), invalid.details());
    assertEquals(
        CommittedRegressionSeedFixtures.expectation(
                JazzerHarness.cliRequest(), "invalid_amount_exponent.json")
            .message(),
        invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForDuplicateObjectKeyCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            CommittedRegressionSeedFixtures.cliRequest("invalid_duplicate_idempotency_key.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedCliRequestReplayDetails(), invalid.details());
    assertEquals(
        CommittedRegressionSeedFixtures.expectation(
                JazzerHarness.cliRequest(), "invalid_duplicate_idempotency_key.json")
            .message(),
        invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForUnexpectedTopLevelFieldCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            CommittedRegressionSeedFixtures.cliRequest("invalid_unexpected_top_level_field.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedCliRequestReplayDetails(), invalid.details());
    assertEquals(
        CommittedRegressionSeedFixtures.expectation(
                JazzerHarness.cliRequest(), "invalid_unexpected_top_level_field.json")
            .message(),
        invalid.message());
  }
}
