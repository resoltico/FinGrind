package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** Covers deterministic ledger-plan fuzz entry behavior. */
class LedgerPlanRequestFuzzAssertionsTest {
  @Test
  void helper_executes_valid_and_rejected_plans_and_ignores_invalid_shapes() {
    assertDoesNotThrow(
        () ->
            LedgerPlanRequestFuzzAssertions.readLedgerPlan(
                CliFuzzHarnessTestSupport.basicValidLedgerPlanBytes()));
    assertDoesNotThrow(
        () ->
            LedgerPlanRequestFuzzAssertions.readLedgerPlan(
                CliFuzzHarnessTestSupport.rejectedMissingBookListPostingsLedgerPlanBytes()));
    assertDoesNotThrow(
        () ->
            LedgerPlanRequestFuzzAssertions.readLedgerPlan(
                CliFuzzHarnessTestSupport.invalidLedgerPlanBytes()));
  }

  @Test
  void fuzz_entrypoint_consumes_provider_for_valid_and_invalid_plans() {
    LedgerPlanRequestFuzzTest harness = new LedgerPlanRequestFuzzTest();
    assertDoesNotThrow(
        () ->
            harness.readLedgerPlan(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.basicValidLedgerPlanBytes())));
    assertDoesNotThrow(
        () ->
            harness.readLedgerPlan(
                CliFuzzHarnessTestSupport.fuzzedBytes(
                    CliFuzzHarnessTestSupport.invalidLedgerPlanBytes())));
  }
}
