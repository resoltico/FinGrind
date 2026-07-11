package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.FuzzHarnessInvocationSupport;
import org.junit.jupiter.api.Test;

/** Covers deterministic weighted-average inventory-costing fuzz entry behavior. */
class WeightedAverageCostingMathFuzzAssertionsTest {
  @Test
  void helper_accepts_arbitrary_seed_bytes_without_violating_exact_costing_invariants() {
    assertDoesNotThrow(() -> JazzerInventoryCostingMathSupport.assertExactPoolMath(new byte[0]));
    assertDoesNotThrow(
        () ->
            JazzerInventoryCostingMathSupport.assertExactPoolMath(
                new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
    assertDoesNotThrow(
        () ->
            JazzerInventoryCostingMathSupport.assertExactPoolMath(
                "inventory-costing".getBytes(UTF_8)));
  }

  @Test
  void fuzz_entrypoint_consumes_provider_for_small_and_large_seed_shapes() {
    assertDoesNotThrow(
        () ->
            JazzerInventoryCostingMathEntrypoints.disposeUsesExactPoolMath(
                FuzzHarnessInvocationSupport.fuzzedBytes(new byte[0])));
    assertDoesNotThrow(
        () ->
            JazzerInventoryCostingMathEntrypoints.disposeUsesExactPoolMath(
                FuzzHarnessInvocationSupport.fuzzedBytes(new byte[] {9, 8, 7, 6, 5, 4, 3, 2, 1})));
    assertDoesNotThrow(
        () ->
            JazzerInventoryCostingMathEntrypoints.disposeUsesExactPoolMath(
                FuzzHarnessInvocationSupport.fuzzedBytes(new byte[] {42, 17, 99, 12})));
  }

  @Test
  void replayDetails_publishSeedDerivedProjectionMismatchFacts() {
    InventoryCostingMathReplayDetails details =
        JazzerInventoryCostingMathSupport.replayDetails(new byte[] {2});

    assertTrue(details.knownProjectionMismatchCaseProven());
    assertTrue(details.seedDerivedProjectionMismatchCaseProven());
    assertEquals("0.07", details.samplePool().costPool());
    assertEquals("0.05", details.sampleExercise().exactCostOfSales());
    assertNotEquals(
        details.sampleExercise().exactCostOfSales(),
        details.sampleExercise().projectionBasedCostOfSales());
  }
}
