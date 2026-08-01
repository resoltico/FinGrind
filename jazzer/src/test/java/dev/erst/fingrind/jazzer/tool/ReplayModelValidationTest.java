package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.core.SourceChannel;
import org.junit.jupiter.api.Test;

/** Pins constructor invariants for Jazzer replay model records. */
class ReplayModelValidationTest {
  @Test
  void replayDetails_trimTextAndRejectBlankValues() {
    ParsedPostingCommandDetails request =
        new ParsedPostingCommandDetails(" 2026-04-07 ", " idem-1 ", 2, false);
    CliRequestReplayDetails details = new CliRequestReplayDetails(request, SourceChannel.CLI);
    ReplayExpectation expectation =
        new ReplayExpectation(ReplayOutcomeKind.SUCCESS, " ok ", details);

    assertEquals("2026-04-07", details.request().effectiveDate());
    assertEquals("idem-1", details.request().idempotencyKey());
    assertEquals(SourceChannel.CLI, details.sourceChannel());
    assertEquals("ok", expectation.message());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerPlanShapeDetails(
                " plan-1 ",
                1,
                LedgerStepKind.DECLARE_ACCOUNT,
                LedgerStepKind.INSPECT_BOOK,
                -1,
                true));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerPlanExecutionDetails(LedgerPlanStatus.SUCCEEDED, 1, 0, 1));
  }

  @Test
  void replayOutcomeAndHarnessDescriptor_rejectBlankFields() {
    CliRequestReplayDetails details =
        new CliRequestReplayDetails(
            new ParsedPostingCommandDetails("2026-04-07", "idem-1", 2, false), SourceChannel.CLI);

    ReplayOutcome.Success success = new ReplayOutcome.Success(" cli-request ", details);

    assertEquals("cli-request", success.harnessKey());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReplayOutcome.UnexpectedFailure(" ", "bug", "boom", "stack", details));
    assertEquals(
        "fuzz",
        new JazzerHarnessRunner.HarnessDescriptor(" harness.Fixture ", " fuzz ").methodName());
    assertThrows(
        IllegalArgumentException.class,
        () -> new JazzerHarnessRunner.HarnessDescriptor("harness.Fixture", " "));
  }
}
