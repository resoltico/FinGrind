package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import org.junit.jupiter.api.Test;

/** Covers deterministic direct replay for CLI request seeds. */
class JazzerReplayCliRequestTest {
  @Test
  void replay_returnsSuccessForValidCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            "PARSED", "2026-04-07", "idem-1", 2, false, "AGENT", "CLI", "NONE"),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForForbiddenRecordedAtCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            JazzerReplayRequestFixtures.invalidForbiddenRecordedAtRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_PARSED",
            "NOT_PARSED",
            "Field is no longer accepted: recordedAt"),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForForbiddenSourceChannelCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            JazzerReplayRequestFixtures.invalidForbiddenSourceChannelRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_PARSED",
            "NOT_PARSED",
            "Field is no longer accepted: sourceChannel"),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            JazzerReplayRequestFixtures.invalidMissingProvenanceRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_PARSED",
            "NOT_PARSED",
            "Missing required field: provenance"),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForExponentAmountCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            JazzerReplayRequestFixtures.invalidExponentAmountRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_PARSED",
            "NOT_PARSED",
            "Money amount must be a plain decimal string without exponent notation."),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForDuplicateObjectKeyCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            JazzerReplayRequestFixtures.invalidDuplicateIdempotencyKeyRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_PARSED",
            "NOT_PARSED",
            "Request JSON must not contain duplicate object keys. Duplicate key: idempotencyKey"),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForUnexpectedTopLevelFieldCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.cliRequest(),
            JazzerReplayRequestFixtures.invalidUnexpectedTopLevelFieldRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_PARSED",
            "NOT_PARSED",
            "Unexpected field: unexpectedField"),
        invalid.details());
  }
}
