package dev.erst.fingrind.jazzer.tool;

import java.io.IOException;
import java.nio.file.Path;

/** Renders replay results for text-mode Jazzer CLI output. */
final class JazzerReplayTextRenderer {
  private JazzerReplayTextRenderer() {}

  static String render(Path inputPath, ReplayOutcome outcome) throws IOException {
    return String.join(
        System.lineSeparator(),
        "Input: " + inputPath,
        "Harness: " + outcome.harnessKey(),
        "Outcome: " + outcome.kind().wireValue(),
        "Message: " + outcome.message(),
        "Details:",
        JazzerJson.toJson(outcome.details()));
  }
}
