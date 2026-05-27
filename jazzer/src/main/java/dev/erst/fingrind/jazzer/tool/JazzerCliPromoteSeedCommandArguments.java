package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerRunTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Parsed `promote-seed` subcommand arguments. */
record JazzerCliPromoteSeedCommandArguments(
    JazzerRunTarget target,
    Path inputPath,
    String seedName,
    String coverageIntent,
    boolean jsonOutput) {
  JazzerCliPromoteSeedCommandArguments {
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(inputPath, "inputPath must not be null");
    seedName = ReplayModelValidation.requireText(seedName, "seedName");
    coverageIntent = ReplayModelValidation.requireText(coverageIntent, "coverageIntent");
  }

  static JazzerCliPromoteSeedCommandArguments parse(List<String> args) {
    String targetKey = requireTargetKey(args);
    Path normalizedInputPath = requireInputPath(args);
    PromoteSeedOptions options = PromoteSeedOptions.parse(args.subList(2, args.size()));
    JazzerRunTarget target = requireReplayableTarget(targetKey);
    return new JazzerCliPromoteSeedCommandArguments(
        target,
        normalizedInputPath,
        RegressionSeedPromoter.normalizeSeedName(options.seedName()),
        options.coverageIntent(),
        options.jsonOutput());
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
          "Seed promotion input path does not exist: " + normalizedInputPath);
    }
    if (!Files.isRegularFile(normalizedInputPath)) {
      throw new IllegalArgumentException(
          "Seed promotion input path must be a regular file: " + normalizedInputPath);
    }
    return normalizedInputPath;
  }

  private static JazzerRunTarget requireReplayableTarget(String targetKey) {
    JazzerRunTarget target = JazzerRunTarget.fromKey(targetKey);
    if (!target.replayable()) {
      throw new IllegalArgumentException(
          "Seed promotion requires a single-harness target, not " + target.key());
    }
    return target;
  }

  private static String requireNextValue(
      ListIterator<String> arguments, String option, String valueDescription) {
    if (!arguments.hasNext()) {
      throw new IllegalArgumentException("Missing " + valueDescription + " after " + option + '.');
    }
    String value = arguments.next();
    if (value.startsWith("-")) {
      throw new IllegalArgumentException("Missing " + valueDescription + " after " + option + '.');
    }
    return value;
  }

  private record PromoteSeedOptions(String seedName, String coverageIntent, boolean jsonOutput) {
    private static PromoteSeedOptions parse(List<String> args) {
      String seedName = null;
      String coverageIntent = null;
      boolean jsonOutput = false;
      ListIterator<String> arguments = args.listIterator();
      while (arguments.hasNext()) {
        String argument = arguments.next();
        switch (argument) {
          case "--name" -> seedName = recordSeedName(arguments, seedName);
          case "--intent" -> coverageIntent = recordCoverageIntent(arguments, coverageIntent);
          case "--json" -> jsonOutput = true;
          default ->
              throw new IllegalArgumentException("Unexpected promote-seed argument: " + argument);
        }
      }
      return new PromoteSeedOptions(
          requireOption(seedName, "--name"), requireOption(coverageIntent, "--intent"), jsonOutput);
    }

    private static String recordSeedName(
        ListIterator<String> arguments, @Nullable String existingSeedName) {
      if (existingSeedName != null) {
        throw new IllegalArgumentException("Duplicate promote-seed option: --name");
      }
      return requireNextValue(arguments, "--name", "promote-seed seed name");
    }

    private static String recordCoverageIntent(
        ListIterator<String> arguments, @Nullable String existingCoverageIntent) {
      if (existingCoverageIntent != null) {
        throw new IllegalArgumentException("Duplicate promote-seed option: --intent");
      }
      return requireNextValue(arguments, "--intent", "promote-seed coverage intent");
    }

    private static String requireOption(@Nullable String value, String option) {
      if (value == null) {
        throw new IllegalArgumentException("Missing required promote-seed option: " + option + '.');
      }
      return value;
    }
  }
}
