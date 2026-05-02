package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.jazzer.support.JazzerTestProjectRoot;
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
  private static final Path PROJECT_DIRECTORY = JazzerTestProjectRoot.projectDirectory();
  private static final Path METADATA_ROOT =
      PROJECT_DIRECTORY.resolve("src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata");

  @TempDir Path tempDirectory;

  @Test
  void committedMetadataInputPathsAreProjectRelative() throws IOException {
    try (Stream<Path> stream = Files.walk(METADATA_ROOT)) {
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
    try (Stream<Path> stream = Files.walk(METADATA_ROOT)) {
      for (Path metadataPath :
          stream
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .sorted()
              .toList()) {
        RegressionSeedMetadata metadata =
            JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        assertTrue(
            Files.exists(metadata.inputPath(PROJECT_DIRECTORY)),
            "committed regression input must exist for " + metadataPath.getFileName());
      }
    }
  }

  @Test
  void everyInputFileHasRegressionMetadata() throws IOException {
    List<JazzerHarness> replayableHarnesses =
        Arrays.stream(JazzerHarness.values())
            .filter(harness -> Files.isDirectory(harness.inputDirectory(PROJECT_DIRECTORY)))
            .toList();

    List<Path> orphans =
        replayableHarnesses.stream()
            .flatMap(
                harness -> {
                  try {
                    return RegressionSeedCatalog.orphanedInputs(PROJECT_DIRECTORY, harness)
                        .stream();
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
