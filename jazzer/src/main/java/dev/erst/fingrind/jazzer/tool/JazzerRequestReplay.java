package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzSupport;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.jazzer.support.JazzerHarness;

/** Replays raw request-parsing harnesses outside active fuzzing. */
final class JazzerRequestReplay {
  private JazzerRequestReplay() {}

  static ReplayOutcome replayCliRequest(byte[] input) {
    try {
      PostEntryCommand command = CliFuzzSupport.readPostEntryCommand(input);
      return new ReplayOutcome.Success(
          JazzerHarness.cliRequest().key(),
          JazzerReplayDetailsSupport.cliRequestDetails(
              command, "PARSED", JazzerReplayDetailsSupport.normalizedMessage(null)));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.cliRequest().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayDetailsSupport.normalizedMessage(expected),
          JazzerReplayDetailsSupport.cliRequestFailureDetails("INVALID_REQUEST", expected));
    } catch (RuntimeException unexpected) {
      return JazzerReplayDetailsSupport.unexpectedFailure(
          JazzerHarness.cliRequest(),
          unexpected,
          JazzerReplayDetailsSupport.cliRequestFailureDetails("UNEXPECTED_FAILURE", unexpected));
    }
  }

  static ReplayOutcome replayLedgerPlanRequest(byte[] input) {
    try {
      LedgerPlan plan = CliFuzzSupport.readLedgerPlan(input);
      return new ReplayOutcome.Success(
          JazzerHarness.ledgerPlanRequest().key(),
          JazzerReplayDetailsSupport.ledgerPlanDetails(
              plan, "PARSED", JazzerReplayDetailsSupport.normalizedMessage(null)));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.ledgerPlanRequest().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayDetailsSupport.normalizedMessage(expected),
          JazzerReplayDetailsSupport.ledgerPlanFailureDetails("INVALID_REQUEST", expected));
    } catch (RuntimeException unexpected) {
      return JazzerReplayDetailsSupport.unexpectedFailure(
          JazzerHarness.ledgerPlanRequest(),
          unexpected,
          JazzerReplayDetailsSupport.ledgerPlanFailureDetails("UNEXPECTED_FAILURE", unexpected));
    }
  }
}
