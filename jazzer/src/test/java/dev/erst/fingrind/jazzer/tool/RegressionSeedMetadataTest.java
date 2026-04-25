package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the structural contract for committed FinGrind regression-seed metadata. */
class RegressionSeedMetadataTest {
  @TempDir Path tempDirectory;

  @Test
  void committedMetadataInputPathsAreProjectRelative() throws IOException {
    Path metadataRoot = Path.of("src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata");
    try (Stream<Path> stream = Files.walk(metadataRoot)) {
      for (Path metadataPath :
          stream
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .sorted()
              .toList()) {
        RegressionSeedMetadata metadata =
            JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        assertFalse(Path.of(metadata.inputPath()).isAbsolute(), "input path must be relative");
      }
    }
  }

  @Test
  void committedMetadataInputPathsResolveWithinProjectDirectory() throws IOException {
    Path projectDirectory = Path.of("").toAbsolutePath().normalize();
    Path metadataRoot = Path.of("src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata");
    try (Stream<Path> stream = Files.walk(metadataRoot)) {
      for (Path metadataPath :
          stream
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .sorted()
              .toList()) {
        RegressionSeedMetadata metadata =
            JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
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
                    return RegressionSeedCatalog.orphanedInputs(projectDirectory, harness).stream();
                  } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                  }
                })
            .sorted()
            .toList();

    assertEquals(
        List.of(), orphans, "Every committed input file must have a regression-metadata entry.");
  }

  @Test
  void catalog_helpers_handle_missing_directories_and_detect_orphaned_inputs() throws IOException {
    assertEquals(
        List.of(), RegressionSeedCatalog.metadataPaths(tempDirectory, JazzerHarness.cliRequest()));
    assertEquals(
        List.of(), RegressionSeedCatalog.inputPaths(tempDirectory, JazzerHarness.cliRequest()));
    assertEquals(
        List.of(), RegressionSeedCatalog.orphanedInputs(tempDirectory, JazzerHarness.cliRequest()));

    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(tempDirectory);
    Path metadataDirectory =
        RegressionSeedCatalog.metadataDirectory(tempDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);
    Path orphanInput = inputDirectory.resolve("orphan.json");
    Files.writeString(orphanInput, JazzerReplayRequestFixtures.basicValidRequest());

    assertEquals(
        List.of(orphanInput.toAbsolutePath().normalize()),
        RegressionSeedCatalog.orphanedInputs(tempDirectory, JazzerHarness.cliRequest()));
  }

  @Test
  void metadata_constructor_normalizes_relative_paths_and_rejects_invalid_shapes() {
    RegressionSeedMetadata metadata =
        new RegressionSeedMetadata(
            " cli-request ",
            " src/fuzz/resources/../resources/basic_valid.json ",
            new ReplayExpectation(
                ReplayOutcomeKind.SUCCESS,
                ReplayOutcome.SUCCESS_MESSAGE,
                new UnparsedCliRequestReplayDetails()));

    assertEquals("src/fuzz/resources/basic_valid.json", metadata.inputPath());
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegressionSeedMetadata(" ", "relative.json", metadata.expectation()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedMetadata(
                "cli-request", Path.of("/tmp/absolute.json").toString(), metadata.expectation()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegressionSeedMetadata("cli-request", ".", metadata.expectation()));
  }
}
