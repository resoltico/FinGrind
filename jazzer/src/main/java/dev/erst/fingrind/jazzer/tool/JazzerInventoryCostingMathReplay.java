package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Replays raw inventory-costing fuzz inputs outside active fuzzing and classifies their outcome.
 */
final class JazzerInventoryCostingMathReplay {
  private JazzerInventoryCostingMathReplay() {}

  static ReplayOutcome replay(byte[] input) {
    return replay(
        input,
        JazzerInventoryCostingMathSupport::replayDetails,
        JazzerInventoryCostingMathSupport::assertExactPoolMath);
  }

  static ReplayOutcome replay(
      byte[] input,
      Function<byte[], InventoryCostingMathReplayDetails> detailsFactory,
      Consumer<byte[]> assertionExecutor) {
    Objects.requireNonNull(input, "input must not be null");
    InventoryCostingMathReplayDetails details = detailsFactory.apply(input);
    try {
      assertionExecutor.accept(input);
      return new ReplayOutcome.Success(JazzerHarness.inventoryCostingMath().key(), details);
    } catch (RuntimeException | AssertionError unexpected) {
      return JazzerReplayOutcomeSupport.unexpectedFailure(
          JazzerHarness.inventoryCostingMath(), unexpected, details);
    }
  }
}
