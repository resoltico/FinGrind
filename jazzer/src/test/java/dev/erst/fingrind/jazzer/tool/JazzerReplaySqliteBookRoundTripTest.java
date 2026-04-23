package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import org.junit.jupiter.api.Test;

/** Covers deterministic direct replay for SQLite round-trip seeds. */
class JazzerReplaySqliteBookRoundTripTest {
  @Test
  void replay_returnsSuccessForValidSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.sqliteBookRoundTrip(),
            JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new SqliteBookRoundTripReplayDetails(
            "PARSED",
            "2026-04-07",
            "idem-1",
            2,
            false,
            "REJECTED_BOOK_NOT_INITIALIZED",
            "REJECTED_UNKNOWN_ACCOUNT",
            "REJECTED_INACTIVE_ACCOUNT",
            "COMMITTED",
            "RELOADED",
            "REJECTED_DUPLICATE_IDEMPOTENCY_KEY",
            true,
            "NONE"),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForMissingReversalReasonSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.sqliteBookRoundTrip(),
            JazzerReplayRequestFixtures.missingReversalReasonRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new SqliteBookRoundTripReplayDetails(
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
            false,
            "Missing required field: reason"),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.sqliteBookRoundTrip(),
            JazzerReplayRequestFixtures.invalidWrongTypeRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new SqliteBookRoundTripReplayDetails(
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
            false,
            "Field must be a string: effectiveDate"),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForExponentAmountSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.sqliteBookRoundTrip(),
            JazzerReplayRequestFixtures.invalidExponentAmountRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new SqliteBookRoundTripReplayDetails(
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
            false,
            "Money amount must be a plain decimal string without exponent notation."),
        invalid.details());
  }
}
