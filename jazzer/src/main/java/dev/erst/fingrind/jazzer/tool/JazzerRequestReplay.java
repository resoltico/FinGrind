package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.jazzer.support.JazzerHarness;

/** Replays raw request-parsing harnesses outside active fuzzing. */
final class JazzerRequestReplay {
  private JazzerRequestReplay() {}

  static ReplayOutcome replayCliRequest(byte[] input) {
    try {
      PostEntryCommand command = CliFuzzFixtures.readPostEntryCommand(input);
      return new ReplayOutcome.Success(
          JazzerHarness.cliRequest().key(),
          JazzerReplayDetailsMapper.cliRequestDetails(
              command, "PARSED", JazzerReplayDetailsMapper.normalizedMessage(null)));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.cliRequest().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayDetailsMapper.normalizedMessage(expected),
          JazzerReplayDetailsMapper.cliRequestFailureDetails("INVALID_REQUEST", expected));
    } catch (RuntimeException unexpected) {
      return JazzerReplayDetailsMapper.unexpectedFailure(
          JazzerHarness.cliRequest(),
          unexpected,
          JazzerReplayDetailsMapper.cliRequestFailureDetails("UNEXPECTED_FAILURE", unexpected));
    }
  }

  static ReplayOutcome replayLedgerPlanRequest(byte[] input) {
    try {
      LedgerPlan plan = CliFuzzFixtures.readLedgerPlan(input);
      return new ReplayOutcome.Success(
          JazzerHarness.ledgerPlanRequest().key(),
          JazzerReplayDetailsMapper.ledgerPlanDetails(
              plan, "PARSED", JazzerReplayDetailsMapper.normalizedMessage(null)));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.ledgerPlanRequest().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayDetailsMapper.normalizedMessage(expected),
          JazzerReplayDetailsMapper.ledgerPlanFailureDetails("INVALID_REQUEST", expected));
    } catch (RuntimeException unexpected) {
      return JazzerReplayDetailsMapper.unexpectedFailure(
          JazzerHarness.ledgerPlanRequest(),
          unexpected,
          JazzerReplayDetailsMapper.ledgerPlanFailureDetails("UNEXPECTED_FAILURE", unexpected));
    }
  }
}
