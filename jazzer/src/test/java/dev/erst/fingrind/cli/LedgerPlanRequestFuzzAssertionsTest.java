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
                CliFuzzLedgerPlanFixtureSupport.basicValidLedgerPlanBytes()));
    assertDoesNotThrow(
        () ->
            LedgerPlanRequestFuzzAssertions.readLedgerPlan(
                CliFuzzLedgerPlanFixtureSupport.validJpyLedgerPlanBytes()));
    assertDoesNotThrow(
        () ->
            LedgerPlanRequestFuzzAssertions.readLedgerPlan(
                CliFuzzLedgerPlanFixtureSupport.validBhdLedgerPlanBytes()));
    assertDoesNotThrow(
        () ->
            LedgerPlanRequestFuzzAssertions.readLedgerPlan(
                CliFuzzLedgerPlanFixtureSupport.rejectedMissingBookListPostingsLedgerPlanBytes()));
    assertDoesNotThrow(
        () ->
            LedgerPlanRequestFuzzAssertions.readLedgerPlan(
                CliFuzzLedgerPlanFixtureSupport.invalidLedgerPlanBytes()));
  }

  @Test
  void fuzz_entrypoint_consumes_provider_for_valid_and_invalid_plans() {
    LedgerPlanRequestFuzzTest harness = new LedgerPlanRequestFuzzTest();
    assertDoesNotThrow(
        () ->
            harness.readLedgerPlan(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzLedgerPlanFixtureSupport.basicValidLedgerPlanBytes())));
    assertDoesNotThrow(
        () ->
            harness.readLedgerPlan(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzLedgerPlanFixtureSupport.validJpyLedgerPlanBytes())));
    assertDoesNotThrow(
        () ->
            harness.readLedgerPlan(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzLedgerPlanFixtureSupport.validBhdLedgerPlanBytes())));
    assertDoesNotThrow(
        () ->
            harness.readLedgerPlan(
                CliFuzzHarnessInvocationSupport.fuzzedBytes(
                    CliFuzzLedgerPlanFixtureSupport.invalidLedgerPlanBytes())));
  }
}
