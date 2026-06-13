package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.cli.LedgerPlanFuzzAssertions;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.jazzer.support.JazzerHarness;

/** Replays raw request-parsing harnesses outside active fuzzing. */
final class JazzerRequestReplay {
  private JazzerRequestReplay() {}

  static ReplayOutcome replayCliRequest(byte[] input) {
    return replayCliRequest(input, CliFuzzFixtures::readPostEntryCommand);
  }

  static ReplayOutcome replayCliRequest(byte[] input, PostEntryCommandParser parser) {
    try {
      PostEntryCommand command = parser.parse(input);
      return new ReplayOutcome.Success(
          JazzerHarness.cliRequest().key(), JazzerReplayDetailsMapper.cliRequestDetails(command));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.cliRequest().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayOutcomeSupport.normalizedMessage(expected),
          JazzerReplayDetailsMapper.unparsedCliRequestDetails());
    } catch (RuntimeException unexpected) {
      return JazzerReplayOutcomeSupport.unexpectedFailure(
          JazzerHarness.cliRequest(),
          unexpected,
          JazzerReplayDetailsMapper.unparsedCliRequestDetails());
    }
  }

  static ReplayOutcome replayLedgerPlanRequest(byte[] input) {
    return replayLedgerPlanRequest(
        input, CliFuzzFixtures::readLedgerPlan, LedgerPlanFuzzAssertions::executeAndAssert);
  }

  static ReplayOutcome replayLedgerPlanRequest(
      byte[] input, LedgerPlanParser parser, LedgerPlanExecutor executor) {
    LedgerPlan plan = null;
    try {
      plan = parser.parse(input);
      LedgerPlanFuzzAssertions.ExecutionSnapshot executionSnapshot = executor.execute(plan, input);
      return new ReplayOutcome.Success(
          JazzerHarness.ledgerPlanRequest().key(),
          JazzerReplayDetailsMapper.ledgerPlanDetails(plan, executionSnapshot));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.ledgerPlanRequest().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayOutcomeSupport.normalizedMessage(expected),
          JazzerReplayDetailsMapper.unparsedLedgerPlanDetails());
    } catch (RuntimeException unexpected) {
      return JazzerReplayOutcomeSupport.unexpectedFailure(
          JazzerHarness.ledgerPlanRequest(),
          unexpected,
          plan == null
              ? JazzerReplayDetailsMapper.unparsedLedgerPlanDetails()
              : JazzerReplayDetailsMapper.parsedLedgerPlanShapeDetails(plan));
    }
  }

  /** Parses one raw CLI-request replay input into the production posting command model. */
  @FunctionalInterface
  interface PostEntryCommandParser {
    /** Parses one raw replay payload into a posting command. */
    PostEntryCommand parse(byte[] input);
  }

  /** Parses one raw ledger-plan replay input into the production ledger-plan model. */
  @FunctionalInterface
  interface LedgerPlanParser {
    /** Parses one raw replay payload into a ledger plan. */
    LedgerPlan parse(byte[] input);
  }

  /** Executes one parsed ledger plan and returns the deterministic replay snapshot. */
  @FunctionalInterface
  interface LedgerPlanExecutor {
    /** Executes one parsed plan and returns the deterministic replay snapshot. */
    LedgerPlanFuzzAssertions.ExecutionSnapshot execute(LedgerPlan plan, byte[] input);
  }
}
