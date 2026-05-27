package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Owns committed regression-seed path discovery. */
final class RegressionSeedPaths {
  private RegressionSeedPaths() {}

  static Path metadataDirectory(Path projectDirectory, JazzerHarness harness) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    return harness.regressionMetadataDirectory(projectDirectory.toAbsolutePath().normalize());
  }

  static List<Path> metadataPaths(Path projectDirectory, JazzerHarness harness) throws IOException {
    Path metadataDirectory = metadataDirectory(projectDirectory, harness);
    if (!Files.isDirectory(metadataDirectory)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.walk(metadataDirectory)) {
      return stream
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .sorted()
          .toList();
    }
  }

  static List<Path> inputPaths(Path projectDirectory, JazzerHarness harness) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    Path inputDirectory = harness.inputDirectory(projectDirectory.toAbsolutePath().normalize());
    if (!Files.isDirectory(inputDirectory)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.list(inputDirectory)) {
      return stream.filter(Files::isRegularFile).sorted().toList();
    }
  }

  static List<Path> allInputPaths(Path projectDirectory) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    List<Path> inputPaths = new ArrayList<>();
    for (JazzerHarness harness : JazzerHarness.values()) {
      inputPaths.addAll(inputPaths(projectDirectory, harness));
    }
    return inputPaths.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList();
  }

  static List<Path> orphanedInputs(Path projectDirectory, JazzerHarness harness)
      throws IOException {
    List<Path> inputs = inputPaths(projectDirectory, harness);
    if (inputs.isEmpty()) {
      return List.of();
    }
    Set<Path> recordedInputs = new HashSet<>();
    for (Path metadataPath : metadataPaths(projectDirectory, harness)) {
      try {
        RegressionSeedMetadata metadata =
            JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        recordedInputs.add(metadata.inputPath(projectDirectory).toAbsolutePath().normalize());
      } catch (IOException | RuntimeException exception) {
        throw new IllegalStateException(
            "Committed regression metadata is invalid: "
                + metadataPath.toAbsolutePath().normalize()
                + " -> "
                + exception.getMessage(),
            exception);
      }
    }
    return inputs.stream()
        .map(path -> path.toAbsolutePath().normalize())
        .filter(path -> !recordedInputs.contains(path))
        .sorted()
        .toList();
  }
}
