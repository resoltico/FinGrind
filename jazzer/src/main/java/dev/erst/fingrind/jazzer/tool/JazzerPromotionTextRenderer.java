package dev.erst.fingrind.jazzer.tool;

import java.io.IOException;

/** Renders committed seed-promotion results for text-mode Jazzer CLI output. */
final class JazzerPromotionTextRenderer {
  private JazzerPromotionTextRenderer() {}

  static String render(RegressionSeedPromotionResult result) throws IOException {
    return String.join(
        System.lineSeparator(),
        "Target: " + result.targetKey(),
        "Source: " + result.sourceInputPath(),
        "Committed input: " + result.committedInputPath(),
        "Metadata: " + result.metadataPath(),
        "Coverage intent: " + result.coverageIntent(),
        "Expectation:",
        JazzerJson.toJson(result.expectation()));
  }
}
