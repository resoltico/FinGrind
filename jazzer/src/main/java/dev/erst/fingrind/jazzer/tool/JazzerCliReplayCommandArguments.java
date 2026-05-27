package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerRunTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Parsed `replay` subcommand arguments. */
record JazzerCliReplayCommandArguments(JazzerRunTarget target, Path inputPath, boolean jsonOutput) {
  JazzerCliReplayCommandArguments {
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(inputPath, "inputPath must not be null");
  }

  static JazzerCliReplayCommandArguments parse(List<String> args) {
    String targetKey = requireTargetKey(args);
    Path normalizedInputPath = requireInputPath(args);
    boolean jsonOutput = requireJsonFlag(args);
    JazzerRunTarget target = requireReplayableTarget(targetKey);
    return new JazzerCliReplayCommandArguments(target, normalizedInputPath, jsonOutput);
  }

  private static String requireTargetKey(List<String> args) {
    if (args.isEmpty() || args.getFirst().startsWith("-")) {
      throw new IllegalArgumentException("Missing required target key.");
    }
    return args.getFirst();
  }

  private static Path requireInputPath(List<String> args) {
    if (args.size() < 2 || args.get(1).startsWith("-")) {
      throw new IllegalArgumentException("Missing required input path.");
    }
    Path normalizedInputPath = Path.of(args.get(1)).toAbsolutePath().normalize();
    if (!Files.exists(normalizedInputPath)) {
      throw new IllegalArgumentException(
          "Replay input path does not exist: " + normalizedInputPath);
    }
    if (!Files.isRegularFile(normalizedInputPath)) {
      throw new IllegalArgumentException(
          "Replay input path must be a regular file: " + normalizedInputPath);
    }
    return normalizedInputPath;
  }

  private static boolean requireJsonFlag(List<String> args) {
    if (args.size() <= 2) {
      return false;
    }
    if (!"--json".equals(args.get(2))) {
      throw new IllegalArgumentException("Unexpected replay argument: " + args.get(2));
    }
    if (args.size() > 3) {
      throw new IllegalArgumentException("Unexpected replay argument: " + args.get(3));
    }
    return true;
  }

  private static JazzerRunTarget requireReplayableTarget(String targetKey) {
    JazzerRunTarget target = JazzerRunTarget.fromKey(targetKey);
    if (!target.replayable()) {
      throw new IllegalArgumentException(
          "Replay requires a single-harness target, not " + target.key());
    }
    return target;
  }
}
