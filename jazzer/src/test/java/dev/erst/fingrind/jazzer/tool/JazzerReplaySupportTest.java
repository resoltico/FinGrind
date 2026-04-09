package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import org.junit.jupiter.api.Test;

/** Covers deterministic direct replay for each committed-seed harness family. */
class JazzerReplaySupportTest {
  @Test
  void replay_returnsSuccessForValidCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(JazzerHarness.CLI_REQUEST, basicValidRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            "PARSED", "2026-04-07", "idem-1", 2, false, "AGENT", "CLI", "NONE"),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.CLI_REQUEST, invalidMissingProvenanceRequest().getBytes(UTF_8));

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
  void replay_returnsSuccessForValidPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(JazzerHarness.POSTING_WORKFLOW, basicValidRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new PostingWorkflowReplayDetails(
            "PARSED",
            "2026-04-07",
            "idem-1",
            2,
            false,
            "PREFLIGHT_ACCEPTED",
            "COMMITTED",
            "REJECTED_DUPLICATE_IDEMPOTENCY_KEY",
            true,
            "NONE"),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.POSTING_WORKFLOW, invalidBlankActorRequest().getBytes(UTF_8));

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
            false,
            "Actor id must not be blank."),
        invalid.details());
  }

  @Test
  void replay_returnsSuccessForValidSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.SQLITE_BOOK_ROUND_TRIP, basicValidRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new SqliteBookRoundTripReplayDetails(
            "PARSED",
            "2026-04-07",
            "idem-1",
            2,
            false,
            "COMMITTED",
            "RELOADED",
            "REJECTED_DUPLICATE_IDEMPOTENCY_KEY",
            true,
            "NONE"),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.SQLITE_BOOK_ROUND_TRIP, invalidWrongTypeRequest().getBytes(UTF_8));

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
            false,
            "Field must be a string: effectiveDate"),
        invalid.details());
  }

  private static String basicValidRequest() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            }
          ],
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1",
            "recordedAt": "2026-04-07T10:15:30Z",
            "sourceChannel": "CLI"
          }
        }
        """;
  }

  private static String invalidMissingProvenanceRequest() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            }
          ]
        }
        """;
  }

  private static String invalidBlankActorRequest() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "10.00"
            }
          ],
          "provenance": {
            "actorId": "   ",
            "actorType": "AGENT",
            "commandId": "command-3",
            "idempotencyKey": "idem-3",
            "causationId": "cause-3",
            "sourceChannel": "CLI"
          }
        }
        """;
  }

  private static String invalidWrongTypeRequest() {
    return """
        {
          "effectiveDate": 1,
          "lines": [],
          "provenance": {}
        }
        """;
  }
}
