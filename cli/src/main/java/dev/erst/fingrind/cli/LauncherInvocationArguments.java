package dev.erst.fingrind.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

/** Resolves staged launcher arguments for bundle-internal process handoff seams. */
final class LauncherInvocationArguments {
  static final String ARGUMENTS_FILE_ENV = "FINGRIND_LAUNCHER_ARGUMENTS_FILE";

  private final Map<String, String> environment;
  private final Function<String, Path> pathResolver;

  LauncherInvocationArguments(Map<String, String> environment) {
    this(environment, Path::of);
  }

  LauncherInvocationArguments(
      Map<String, String> environment, Function<String, Path> pathResolver) {
    this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
  }

  static String[] resolveForCurrentProcess(String[] processArguments) {
    return new LauncherInvocationArguments(System.getenv()).resolve(processArguments);
  }

  String[] resolve(String[] processArguments) {
    Objects.requireNonNull(processArguments, "processArguments");
    String argumentsFile = environment.getOrDefault(ARGUMENTS_FILE_ENV, "").trim();
    if (argumentsFile.isEmpty()) {
      return processArguments.clone();
    }
    return readStagedArguments(pathFrom(argumentsFile));
  }

  private Path pathFrom(String rawPath) {
    try {
      return pathResolver.apply(rawPath);
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
          Objects.requireNonNullElseGet(
              CliJsonObjectMappers.configuredObjectMapper()
                  .readTree(Files.readAllBytes(argumentsFile)),
              NullNode::getInstance);
    } catch (IOException | JacksonException exception) {
      if (!Files.exists(argumentsFile)) {
        throw new LauncherInvocationArgumentsException(
            "Unable to read staged launcher arguments file at " + argumentsFile + ".", exception);
      }
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
