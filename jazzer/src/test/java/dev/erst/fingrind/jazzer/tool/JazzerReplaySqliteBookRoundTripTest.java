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
            new ParsedPostingCommandDetails("2026-04-07", "idem-1", 2, false),
            new SqliteBookRoundTripLifecycleDetails(
                PostingLifecycleStatus.BOOK_NOT_INITIALIZED,
                PostingLifecycleStatus.UNKNOWN_ACCOUNT,
                PostingLifecycleStatus.INACTIVE_ACCOUNT),
            new SqliteBookRoundTripOutcomeDetails(
                PostingLifecycleStatus.COMMITTED,
                PostingLifecycleStatus.RELOADED,
                PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
                true)),
        success.details());
  }

  @Test
  void replay_returnsSuccessForReversalTargetMissingSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.sqliteBookRoundTrip(),
            JazzerReplayRequestFixtures.reversalTargetMissingRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new SqliteBookRoundTripReplayDetails(
            new ParsedPostingCommandDetails("2026-04-08", "idem-5", 2, true),
            new SqliteBookRoundTripLifecycleDetails(
                PostingLifecycleStatus.BOOK_NOT_INITIALIZED,
                PostingLifecycleStatus.UNKNOWN_ACCOUNT,
                PostingLifecycleStatus.INACTIVE_ACCOUNT),
            new SqliteBookRoundTripOutcomeDetails(
                PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
                PostingLifecycleStatus.NOT_RUN,
                PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
                false)),
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
    assertEquals(new UnparsedSqliteBookRoundTripReplayDetails(), invalid.details());
    assertEquals("Missing required field: reason", invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.sqliteBookRoundTrip(),
            JazzerReplayRequestFixtures.invalidWrongTypeRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedSqliteBookRoundTripReplayDetails(), invalid.details());
    assertEquals("Field must be a string: effectiveDate", invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForExponentAmountSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.sqliteBookRoundTrip(),
            JazzerReplayRequestFixtures.invalidExponentAmountRequest().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedSqliteBookRoundTripReplayDetails(), invalid.details());
    assertEquals(
        "Money amount must be a plain decimal string without exponent notation.",
        invalid.message());
  }
}
