package dev.erst.fingrind.jazzer.tool;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Parsed top-level Jazzer CLI arguments after project-root dispatch handling. */
record JazzerCliMainArguments(Path projectDirectory, List<String> commandArguments) {
  static final String PROJECT_ROOT_OPTION = "--project-root";
  static final Path UNUSED_PROJECT_DIRECTORY =
      Path.of(System.getProperty("java.io.tmpdir")).resolve("fingrind-unused-jazzer-root");

  static JazzerCliMainArguments parse(String[] arguments) {
    Objects.requireNonNull(arguments, "arguments must not be null");
    if (allowsProjectRootlessInvocation(arguments)) {
      return new JazzerCliMainArguments(UNUSED_PROJECT_DIRECTORY, List.of(arguments.clone()));
    }
    requireProjectRootInvocation(arguments);
    String projectRoot = arguments[1];
    if (projectRoot.isBlank()) {
      throw new IllegalArgumentException(PROJECT_ROOT_OPTION + " must not be blank");
    }
    return new JazzerCliMainArguments(
        Path.of(projectRoot).toAbsolutePath().normalize(),
        List.of(Arrays.copyOfRange(arguments, 2, arguments.length)));
  }

  private static boolean allowsProjectRootlessInvocation(String[] arguments) {
    if (arguments.length == 0) {
      return true;
    }
    String firstArgument = arguments[0];
    return (arguments.length == 1 && "--help".equals(firstArgument))
        || "active-target-keys".equals(firstArgument);
  }

  private static void requireProjectRootInvocation(String[] arguments) {
    if (arguments.length < 3) {
      throw usageException();
    }
    if (!PROJECT_ROOT_OPTION.equals(arguments[0])) {
      throw usageException();
    }
  }

  private static IllegalArgumentException usageException() {
    return new IllegalArgumentException(
        "Usage: JazzerCli "
            + PROJECT_ROOT_OPTION
            + " <jazzer-project-dir> <command> [command-options]");
  }
}
