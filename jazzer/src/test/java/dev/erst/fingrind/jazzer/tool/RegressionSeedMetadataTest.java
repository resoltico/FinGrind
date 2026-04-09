package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Verifies the structural contract for committed FinGrind regression-seed metadata. */
class RegressionSeedMetadataTest {
  @Test
  void committedMetadataInputPathsAreProjectRelative() throws IOException {
    Path metadataRoot =
        Path.of("src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata");
    try (Stream<Path> stream = Files.walk(metadataRoot)) {
      for (Path metadataPath :
          stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
        RegressionSeedMetadata metadata = JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        assertFalse(Path.of(metadata.inputPath()).isAbsolute(), "input path must be relative");
      }
    }
  }

  @Test
  void committedMetadataInputPathsResolveWithinProjectDirectory() throws IOException {
    Path projectDirectory = Path.of("").toAbsolutePath().normalize();
    Path metadataRoot =
        Path.of("src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata");
    try (Stream<Path> stream = Files.walk(metadataRoot)) {
      for (Path metadataPath :
          stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
        RegressionSeedMetadata metadata = JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        assertTrue(
            Files.exists(metadata.inputPath(projectDirectory)),
            "committed regression input must exist for " + metadataPath.getFileName());
      }
    }
  }

  @Test
  void everyInputFileHasRegressionMetadata() throws IOException {
    Path projectDirectory = Path.of("").toAbsolutePath().normalize();
    List<JazzerHarness> replayableHarnesses =
        Arrays.stream(JazzerHarness.values())
            .filter(harness -> Files.isDirectory(harness.inputDirectory(projectDirectory)))
            .toList();

    List<Path> orphans =
        replayableHarnesses.stream()
            .flatMap(
                harness -> {
                  try {
                    return RegressionSeedSupport.orphanedInputs(projectDirectory, harness).stream();
                  } catch (IOException exception) {
                    throw new RuntimeException(exception);
                  }
                })
            .sorted()
            .toList();

    assertEquals(
        List.of(),
        orphans,
        "Every committed input file must have a regression-metadata entry.");
  }
}
