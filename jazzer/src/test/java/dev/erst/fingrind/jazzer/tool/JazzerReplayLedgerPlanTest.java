package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import org.junit.jupiter.api.Test;

/** Covers deterministic direct replay for ledger-plan seeds. */
class JazzerReplayLedgerPlanTest {
  @Test
  void replay_returnsSuccessForValidLedgerPlanRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.ledgerPlanRequest(),
            JazzerReplayLedgerPlanFixtures.basicValidLedgerPlan().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new LedgerPlanReplayDetails(
            "PARSED", "plan-1", 5, "open-book", "assert", 1, true, "SUCCEEDED", 5, 0, 0, "NONE"),
        success.details());
  }

  @Test
  void replay_returnsSuccessForStructuredQueryLedgerPlanSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.ledgerPlanRequest(),
            JazzerReplayLedgerPlanFixtures.validLedgerPlanWithQueries().getBytes(UTF_8));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    assertEquals(
        new LedgerPlanReplayDetails(
            "PARSED",
            "plan-query-1",
            6,
            "open-book",
            "list-postings",
            0,
            true,
            "SUCCEEDED",
            6,
            2,
            2,
            "NONE"),
        success.details());
  }

  @Test
  void replay_returnsExpectedInvalidForExecutionPolicyLedgerPlanRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.ledgerPlanRequest(),
            JazzerReplayLedgerPlanFixtures.invalidExecutionPolicyLedgerPlan().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new LedgerPlanReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            0,
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_PARSED",
            0,
            0,
            0,
            "Unexpected field: executionPolicy"),
        invalid.details());
  }

  @Test
  void replay_returnsExpectedInvalidForUnknownKindLedgerPlanRequestSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.ledgerPlanRequest(),
            JazzerReplayLedgerPlanFixtures.invalidUnknownKindLedgerPlan().getBytes(UTF_8));

    ReplayOutcome.ExpectedInvalid invalid =
        assertInstanceOf(ReplayOutcome.ExpectedInvalid.class, outcome);
    assertEquals(
        new LedgerPlanReplayDetails(
            "INVALID_REQUEST",
            "NOT_PARSED",
            0,
            "NOT_PARSED",
            "NOT_PARSED",
            0,
            false,
            "NOT_PARSED",
            0,
            0,
            0,
            "Unsupported value for kind: post_entry. Accepted values: "
                + String.join(", ", LedgerStepKind.wireValues())
                + "."),
        invalid.details());
  }
}
