package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Reads the committed regression-seed metadata that drives deterministic replay. */
public final class RegressionSeedSupport {
  private RegressionSeedSupport() {}

  /** Returns the metadata directory for one harness. */
  public static Path metadataDirectory(Path projectDirectory, JazzerHarness harness) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    return projectDirectory
        .toAbsolutePath()
        .normalize()
        .resolve("src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata")
        .resolve(harness.key());
  }

  /** Returns the committed metadata paths for one harness. */
  public static List<Path> metadataPaths(Path projectDirectory, JazzerHarness harness) throws IOException {
    Path metadataDirectory = metadataDirectory(projectDirectory, harness);
    if (!Files.isDirectory(metadataDirectory)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.walk(metadataDirectory)) {
      return stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
    }
  }

  /** Returns the committed input files for one harness. */
  public static List<Path> inputPaths(Path projectDirectory, JazzerHarness harness) throws IOException {
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

  /** Returns committed inputs with no corresponding regression metadata entry. */
  public static List<Path> orphanedInputs(Path projectDirectory, JazzerHarness harness) throws IOException {
    List<Path> inputs = inputPaths(projectDirectory, harness);
    if (inputs.isEmpty()) {
      return List.of();
    }
    Set<Path> recordedInputs = new HashSet<>();
    for (Path metadataPath : metadataPaths(projectDirectory, harness)) {
      RegressionSeedMetadata metadata = JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
      recordedInputs.add(metadata.inputPath(projectDirectory).toAbsolutePath().normalize());
    }
    return inputs.stream()
        .map(path -> path.toAbsolutePath().normalize())
        .filter(path -> !recordedInputs.contains(path))
        .sorted()
        .toList();
  }
}
