package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.util.Objects;

/** Replays raw committed Jazzer inputs outside active fuzzing and classifies their outcome. */
public final class JazzerReplaySupport {
  private JazzerReplaySupport() {}

  /** Returns the stable replay expectation captured from one replay outcome. */
  public static ReplayExpectation expectationFor(ReplayOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome must not be null");
    return new ReplayExpectation(outcomeKind(outcome), outcome.details());
  }

  /** Replays one raw input against the selected harness and returns a structured outcome. */
  public static ReplayOutcome replay(JazzerHarness harness, byte[] input) {
    Objects.requireNonNull(harness, "harness must not be null");
    Objects.requireNonNull(input, "input must not be null");
    return switch (harness.key()) {
      case "cli-request" -> JazzerRequestReplay.replayCliRequest(input);
      case "ledger-plan-request" -> JazzerRequestReplay.replayLedgerPlanRequest(input);
      case "posting-workflow" -> JazzerPostingWorkflowReplay.replay(input);
      case "sqlite-book-roundtrip" -> JazzerSqliteBookRoundTripReplay.replay(input);
      default -> throw new IllegalArgumentException("Unknown Jazzer harness: " + harness.key());
    };
  }

  /** Returns the stable external outcome kind used in committed seed metadata. */
  public static String outcomeKind(ReplayOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome must not be null");
    return switch (outcome) {
      case ReplayOutcome.Success _ -> "SUCCESS";
      case ReplayOutcome.ExpectedInvalid _ -> "EXPECTED_INVALID";
      case ReplayOutcome.UnexpectedFailure _ -> "UNEXPECTED_FAILURE";
    };
  }
}
