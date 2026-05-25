package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import org.junit.jupiter.api.Test;

/** Covers deterministic direct replay for ledger-plan seeds. */
class JazzerReplayLedgerPlanTest {
  @Test
  void replay_returnsSuccessForValidLedgerPlanRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.ledgerPlanRequest(),
            CommittedRegressionSeedFixtures.ledgerPlanRequest("basic_valid.json").getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new LedgerPlanReplayDetails(
            new LedgerPlanShapeDetails(
                "plan-1", 5, LedgerStepKind.OPEN_BOOK, LedgerStepKind.ASSERT, 1, true),
            new LedgerPlanExecutionDetails(LedgerPlanStatus.SUCCEEDED, 5, 0, 0)),
        success.details());
    assertEquals(ReplayOutcomeKind.SUCCESS, success.kind());
    assertEquals(ReplayOutcome.SUCCESS_MESSAGE, success.message());
  }

  @Test
  void replay_returnsSuccessForStructuredQueryLedgerPlanSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.ledgerPlanRequest(),
            CommittedRegressionSeedFixtures.ledgerPlanRequest("query_valid.json").getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new LedgerPlanReplayDetails(
            new LedgerPlanShapeDetails(
                "plan-query-1", 6, LedgerStepKind.OPEN_BOOK, LedgerStepKind.LIST_POSTINGS, 0, true),
            new LedgerPlanExecutionDetails(LedgerPlanStatus.SUCCEEDED, 6, 2, 2)),
        success.details());
  }

  @Test
  void replay_returnsSuccessForRejectedMissingBookListQueryPlanShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.ledgerPlanRequest(),
            CommittedRegressionSeedFixtures.ledgerPlanRequest(
                    "rejected_missing_book_list_postings.json")
                .getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new LedgerPlanReplayDetails(
            new LedgerPlanShapeDetails(
                "play-1", 1, LedgerStepKind.LIST_POSTINGS, LedgerStepKind.LIST_POSTINGS, 0, false),
            new LedgerPlanExecutionDetails(LedgerPlanStatus.REJECTED, 1, 1, 0)),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForExecutionPolicyLedgerPlanRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.ledgerPlanRequest(),
            CommittedRegressionSeedFixtures.ledgerPlanRequest("invalid_execution_policy.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedLedgerPlanReplayDetails(), invalid.details());
    assertEquals("Unexpected field: executionPolicy", invalid.message());
  }

  @Test
  void replay_returnsExpectedInvalidForUnknownKindLedgerPlanRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.ledgerPlanRequest(),
            CommittedRegressionSeedFixtures.ledgerPlanRequest(
                    "invalid_unknown_kind_without_assertion.json")
                .getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(new UnparsedLedgerPlanReplayDetails(), invalid.details());
    assertEquals(
        "Unsupported value for kind: post_entry. Accepted values: "
            + String.join(", ", LedgerStepKind.wireValues())
            + ".",
        invalid.message());
  }
}
