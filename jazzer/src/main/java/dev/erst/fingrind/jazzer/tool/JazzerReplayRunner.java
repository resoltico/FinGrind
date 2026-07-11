package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.util.Objects;

/** Replays raw committed Jazzer inputs outside active fuzzing and classifies their outcome. */
public final class JazzerReplayRunner {
  private JazzerReplayRunner() {}

  /** Returns the stable replay expectation captured from one replay outcome. */
  public static ReplayExpectation expectationFor(ReplayOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome must not be null");
    return new ReplayExpectation(outcome.kind(), outcome.message(), outcome.details());
  }

  /** Replays one raw input against the selected harness and returns a structured outcome. */
  public static ReplayOutcome replay(JazzerHarness harness, byte[] input) {
    Objects.requireNonNull(harness, "harness must not be null");
    Objects.requireNonNull(input, "input must not be null");
    return switch (harness.kind()) {
      case CLI_REQUEST -> JazzerRequestReplay.replayCliRequest(input);
      case LEDGER_PLAN_REQUEST -> JazzerRequestReplay.replayLedgerPlanRequest(input);
      case POSTING_WORKFLOW -> JazzerPostingWorkflowReplay.replay(input);
      case SQLITE_BOOK_ROUND_TRIP -> JazzerSqliteBookRoundTripReplay.replay(input);
      case INVENTORY_COSTING_MATH -> JazzerInventoryCostingMathReplay.replay(input);
    };
  }
}
