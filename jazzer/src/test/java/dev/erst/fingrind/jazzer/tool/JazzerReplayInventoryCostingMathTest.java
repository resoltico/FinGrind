package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import org.junit.jupiter.api.Test;

/** Covers deterministic direct replay for committed inventory-costing math seeds. */
class JazzerReplayInventoryCostingMathTest {
  @Test
  void replay_returnsSuccessForValidInventoryCostingMathSeedShape() {
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            JazzerHarness.inventoryCostingMath(),
            CommittedRegressionSeedFixtures.inventoryCostingMathBytes("exact_pool_math_seed.bin"));

    ReplayOutcome.Success success = assertInstanceOf(ReplayOutcome.Success.class, outcome);
    ReplayExpectation expectation =
        CommittedRegressionSeedFixtures.expectation(
            JazzerHarness.inventoryCostingMath(), "exact_pool_math_seed.json");
    assertEquals(expectation.message(), success.message());
    assertEquals(expectation.details(), success.details());
  }

  @Test
  void replay_returnsUnexpectedFailureWhenAssertionExecutorThrows() {
    InventoryCostingMathReplayDetails details =
        new InventoryCostingMathReplayDetails(
            7L,
            1,
            true,
            true,
            new InventoryCostingPoolDetails("1", 1L, 0, "1.00", 100L),
            new InventoryCostingExerciseDetails("1", "1.00", "1.00", "1.00", "0", "0.00"));

    ReplayOutcome outcome =
        JazzerInventoryCostingMathReplay.replay(
            new byte[] {1, 2, 3},
            _input -> details,
            _input -> {
              throw new IllegalStateException("boom");
            });

    ReplayOutcome.UnexpectedFailure failure =
        assertInstanceOf(ReplayOutcome.UnexpectedFailure.class, outcome);
    assertEquals("IllegalStateException", failure.failureKind());
    assertEquals("boom", failure.message());
    assertEquals(details, failure.details());
  }
}
