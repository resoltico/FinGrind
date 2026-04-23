package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Pins constructor invariants for Jazzer replay model records. */
class ReplayModelValidationTest {
  @Test
  void replayDetails_trimTextAndRejectBlankValues() {
    CliRequestReplayDetails details =
        new CliRequestReplayDetails(
            " PARSED ",
            " 2026-04-07 ",
            " idem-1 ",
            2,
            false,
            " AGENT ",
            " CLI ",
            " NONE ");

    assertEquals("PARSED", details.requestStatus());
    assertEquals("2026-04-07", details.effectiveDate());
    assertEquals("idem-1", details.idempotencyKey());
    assertEquals("AGENT", details.actorType());
    assertEquals("CLI", details.sourceChannel());
    assertEquals("NONE", details.failureMessage());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerPlanReplayDetails(
                "PARSED",
                "plan-1",
                1,
                "open-book",
                "inspect-book",
                0,
                true,
                "SUCCEEDED",
                1,
                0,
                0,
                " "));
  }

  @Test
  void replayOutcomeAndHarnessDescriptor_rejectBlankFields() {
    CliRequestReplayDetails details =
        new CliRequestReplayDetails(
            "PARSED", "2026-04-07", "idem-1", 2, false, "AGENT", "CLI", "NONE");

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
