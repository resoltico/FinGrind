package dev.erst.fingrind.cli;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/** Resolves staged launcher arguments for bundle-internal process handoff seams. */
final class LauncherInvocationArguments {
  static final String ARGUMENTS_FILE_ENV = "FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE";
  static final int MAXIMUM_STAGED_ARGUMENTS_FILE_BYTES = 1_048_576;

  private final Map<String, String> environment;

  LauncherInvocationArguments(Map<String, String> environment) {
    this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
  }

  static String[] resolveForCurrentProcess(String[] processArguments) {
    return new LauncherInvocationArguments(System.getenv()).resolve(processArguments);
  }

  String[] resolve(String[] processArguments) {
    Objects.requireNonNull(processArguments, "processArguments must not be null");
    String argumentsFile = environment.getOrDefault(ARGUMENTS_FILE_ENV, "").trim();
    if (argumentsFile.isEmpty()) {
      return processArguments.clone();
    }
    return readStagedArguments(pathFrom(argumentsFile));
  }

  private static Path pathFrom(String rawPath) {
    try {
      return Path.of(rawPath);
    } catch (InvalidPathException exception) {
      throw new LauncherInvocationArgumentsException(
          "Invalid staged launcher arguments file path in " + ARGUMENTS_FILE_ENV + ": " + rawPath,
          exception);
    }
  }

  private static String[] readStagedArguments(Path argumentsFile) {
    final JsonNode document;
    try {
      document =
          CliJsonObjectMappers.configuredObjectMapper()
              .readTree(
                  CliNofollowFileInput.readBounded(
                      argumentsFile, MAXIMUM_STAGED_ARGUMENTS_FILE_BYTES));
    } catch (NoSuchFileException exception) {
      throw new LauncherInvocationArgumentsException(
          "Unable to read staged launcher arguments file at " + argumentsFile + ".", exception);
    } catch (IOException | JacksonException exception) {
      throw new LauncherInvocationArgumentsException(
          "Staged launcher arguments file at "
              + argumentsFile
              + " must contain one JSON array of strings.",
          exception);
    }
    if (!document.isArray()) {
      throw new LauncherInvocationArgumentsException(
          "Staged launcher arguments file at "
              + argumentsFile
              + " must contain one JSON array of strings.");
    }
    List<String> resolvedArguments = new ArrayList<>();
    for (JsonNode element : document) {
      if (!element.isString()) {
        throw new LauncherInvocationArgumentsException(
            "Staged launcher arguments file at "
                + argumentsFile
                + " must contain one JSON array of strings.");
      }
      resolvedArguments.add(element.stringValue());
    }
    return resolvedArguments.toArray(String[]::new);
  }
}
