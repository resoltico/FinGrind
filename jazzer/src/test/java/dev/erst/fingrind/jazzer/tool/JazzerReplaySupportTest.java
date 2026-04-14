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
        JazzerReplaySupport.replay(JazzerHarness.cliRequest(), basicValidRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new CliRequestReplayDetails(
            "PARSED", "2026-04-07", "idem-1", 2, false, "AGENT", "CLI", "NONE"),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForForbiddenRecordedAtCliRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.cliRequest(), invalidForbiddenRecordedAtRequest().getBytes(UTF_8));

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
        JazzerReplaySupport.replay(
            JazzerHarness.cliRequest(), invalidForbiddenSourceChannelRequest().getBytes(UTF_8));

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
        JazzerReplaySupport.replay(
            JazzerHarness.cliRequest(), invalidMissingProvenanceRequest().getBytes(UTF_8));

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
        JazzerReplaySupport.replay(
            JazzerHarness.cliRequest(), invalidExponentAmountRequest().getBytes(UTF_8));

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
        JazzerReplaySupport.replay(
            JazzerHarness.cliRequest(), invalidDuplicateIdempotencyKeyRequest().getBytes(UTF_8));

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
        JazzerReplaySupport.replay(
            JazzerHarness.cliRequest(), invalidUnexpectedTopLevelFieldRequest().getBytes(UTF_8));

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

  @Test
  void replay_returnsSuccessForValidPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.postingWorkflow(), basicValidRequest().getBytes(UTF_8));

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
        JazzerReplaySupport.replay(
            JazzerHarness.postingWorkflow(),
            reversalTargetMissingRequest().getBytes(UTF_8));

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
  void replay_returnsExpectedInvalidForInvalidPostingWorkflowSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.postingWorkflow(), invalidBlankActorRequest().getBytes(UTF_8));

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
        JazzerReplaySupport.replay(
            JazzerHarness.postingWorkflow(), invalidExponentAmountRequest().getBytes(UTF_8));

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

  @Test
  void replay_returnsSuccessForValidSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.sqliteBookRoundTrip(), basicValidRequest().getBytes(UTF_8));

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
  void replay_returnsSuccessForReversalReasonRequiredSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.sqliteBookRoundTrip(),
            reversalReasonRequiredRequest().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new SqliteBookRoundTripReplayDetails(
            "PARSED",
            "2026-04-08",
            "idem-6",
            2,
            true,
            "REJECTED_BOOK_NOT_INITIALIZED",
            "REJECTED_UNKNOWN_ACCOUNT",
            "REJECTED_INACTIVE_ACCOUNT",
            "REJECTED_REVERSAL_REASON_REQUIRED",
            "NOT_RUN",
            "REJECTED_REVERSAL_REASON_REQUIRED",
            false,
            "NONE"),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForInvalidSqliteRoundTripSeedShape() {
    ReplayOutcome outcome =
        JazzerReplaySupport.replay(
            JazzerHarness.sqliteBookRoundTrip(), invalidWrongTypeRequest().getBytes(UTF_8));

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
        JazzerReplaySupport.replay(
            JazzerHarness.sqliteBookRoundTrip(), invalidExponentAmountRequest().getBytes(UTF_8));

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
            "causationId": "cause-1"
          }
        }
        """;
  }

  private static String invalidForbiddenRecordedAtRequest() {
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
            "actorId": "actor-4",
            "actorType": "AGENT",
            "commandId": "command-4",
            "idempotencyKey": "idem-4",
            "causationId": "cause-4",
            "recordedAt": "2026-04-07T10:15:30Z"
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

  private static String invalidExponentAmountRequest() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "currencyCode": "EUR",
              "amount": "1e1000000100"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "1.00"
            }
          ],
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1"
          }
        }
        """;
  }

  private static String invalidDuplicateIdempotencyKeyRequest() {
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
            "actorId": "actor-7",
            "actorType": "AGENT",
            "commandId": "command-7",
            "idempotencyKey": "idem-7-a",
            "idempotencyKey": "idem-7-b",
            "causationId": "cause-7"
          }
        }
        """;
  }

  private static String invalidUnexpectedTopLevelFieldRequest() {
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
            "actorId": "actor-8",
            "actorType": "AGENT",
            "commandId": "command-8",
            "idempotencyKey": "idem-8",
            "causationId": "cause-8"
          },
          "unexpectedField": "should-be-rejected"
        }
        """;
  }

  private static String invalidForbiddenSourceChannelRequest() {
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
            "actorId": "actor-7",
            "actorType": "AGENT",
            "commandId": "command-7",
            "idempotencyKey": "idem-7",
            "causationId": "cause-7",
            "sourceChannel": null
          }
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
            "causationId": "cause-3"
          }
        }
        """;
  }

  private static String reversalTargetMissingRequest() {
    return """
        {
          "effectiveDate": "2026-04-08",
          "lines": [
            {
              "accountCode": "5000",
              "side": "CREDIT",
              "currencyCode": "GBP",
              "amount": "123.45"
            },
            {
              "accountCode": "6000",
              "side": "DEBIT",
              "currencyCode": "GBP",
              "amount": "123.45"
            }
          ],
          "reversal": {
            "priorPostingId": "posting-missing"
          },
          "provenance": {
            "actorId": "actor-5",
            "actorType": "USER",
            "commandId": "command-5",
            "idempotencyKey": "idem-5",
            "causationId": "cause-5",
            "reason": "operator reversal"
          }
        }
        """;
  }

  private static String reversalReasonRequiredRequest() {
    return """
        {
          "effectiveDate": "2026-04-08",
          "lines": [
            {
              "accountCode": "3000",
              "side": "CREDIT",
              "currencyCode": "USD",
              "amount": "99.95"
            },
            {
              "accountCode": "4000",
              "side": "DEBIT",
              "currencyCode": "USD",
              "amount": "99.95"
            }
          ],
          "reversal": {
            "priorPostingId": "posting-missing"
          },
          "provenance": {
            "actorId": "actor-6",
            "actorType": "SYSTEM",
            "commandId": "command-6",
            "idempotencyKey": "idem-6",
            "causationId": "cause-6"
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
