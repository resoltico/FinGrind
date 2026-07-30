package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
    Path canonicalProjectDirectory =
        RegressionSeedRepositoryPathAdmission.canonicalProjectDirectory(projectDirectory);
    Path metadataDirectory = harness.regressionMetadataDirectory(canonicalProjectDirectory);
    if (!RegressionSeedRepositoryPathAdmission.hasExistingRealDirectoryTree(
        canonicalProjectDirectory, metadataDirectory)) {
      return List.of();
    }
    requireExistingRealDirectory(metadataDirectory, "metadata");
    try (Stream<Path> stream = Files.walk(metadataDirectory)) {
      List<Path> discoveredPaths = stream.sorted().toList();
      for (Path path : discoveredPaths) {
        if (Files.isSymbolicLink(path)) {
          throw new IOException(
              "Committed regression metadata must not contain symbolic links: " + path);
        }
      }
      List<Path> metadataPaths =
          discoveredPaths.stream()
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .toList();
      for (Path path : metadataPaths) {
        requireExistingRegularFile(path, "metadata");
      }
      return metadataPaths;
    }
  }

  static List<Path> inputPaths(Path projectDirectory, JazzerHarness harness) throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    Path canonicalProjectDirectory =
        RegressionSeedRepositoryPathAdmission.canonicalProjectDirectory(projectDirectory);
    Path inputDirectory = harness.inputDirectory(canonicalProjectDirectory);
    if (!RegressionSeedRepositoryPathAdmission.hasExistingRealDirectoryTree(
        canonicalProjectDirectory, inputDirectory)) {
      return List.of();
    }
    requireExistingRealDirectory(inputDirectory, "input");
    try (Stream<Path> stream = Files.list(inputDirectory)) {
      List<Path> discoveredPaths = stream.sorted().toList();
      for (Path path : discoveredPaths) {
        if (Files.isSymbolicLink(path)) {
          throw new IOException(
              "Committed regression inputs must not contain symbolic links: " + path);
        }
      }
      return discoveredPaths.stream()
          .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          .toList();
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
    Path canonicalProjectDirectory =
        RegressionSeedRepositoryPathAdmission.canonicalProjectDirectory(projectDirectory);
    List<Path> inputs = inputPaths(canonicalProjectDirectory, harness);
    if (inputs.isEmpty()) {
      return List.of();
    }
    Set<Path> recordedInputs = new HashSet<>();
    for (Path metadataPath : metadataPaths(canonicalProjectDirectory, harness)) {
      try {
        RegressionSeedMetadata metadata =
            JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        recordedInputs.add(
            metadata.inputPath(canonicalProjectDirectory).toAbsolutePath().normalize());
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

  static void requireExistingRealDirectory(Path directory, String artifactKind) throws IOException {
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "Committed regression "
              + artifactKind
              + " directory must be an existing real non-symlink directory: "
              + directory);
    }
  }

  static void requireExistingRegularFile(Path file, String artifactKind) throws IOException {
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "Committed regression "
              + artifactKind
              + " must be an existing regular non-symlink file: "
              + file);
    }
  }
}
