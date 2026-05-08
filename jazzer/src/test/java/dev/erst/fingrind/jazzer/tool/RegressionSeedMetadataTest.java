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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
  void committedMetadataCoverageIntentIsNonBlank_andTargetsOwnHarnessInputDirectories()
      throws IOException {
    Set<String> seenCoverageIntents = new HashSet<>();
    for (JazzerHarness harness : JazzerHarness.values()) {
      for (Path metadataPath : RegressionSeedCatalog.metadataPaths(PROJECT_DIRECTORY, harness)) {
        RegressionSeedMetadata metadata =
            JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        assertEquals(harness.key(), metadata.targetKey(), "metadata target must match its harness");
        assertFalse(metadata.coverageIntent().isBlank(), "coverageIntent must not be blank");
        assertTrue(
            seenCoverageIntents.add(metadata.coverageIntent()),
            "coverageIntent must be unique across the committed corpus: "
                + metadata.coverageIntent());
        assertTrue(
            metadata
                .inputPath(PROJECT_DIRECTORY)
                .toAbsolutePath()
                .normalize()
                .startsWith(harness.inputDirectory(PROJECT_DIRECTORY).toAbsolutePath().normalize()),
            "committed regression input must live under the owning harness input directory");
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
  void strict_catalog_helpers_fail_fast_on_invalid_metadata() throws IOException {
    Path metadataDirectory =
        RegressionSeedCatalog.metadataDirectory(tempDirectory, JazzerHarness.cliRequest());
    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(tempDirectory);
    Files.createDirectories(metadataDirectory);
    Files.createDirectories(inputDirectory);
    Files.writeString(
        inputDirectory.resolve("orphan.json"), JazzerReplayRequestFixtures.basicValidRequest());
    Files.writeString(
        metadataDirectory.resolve("broken.json"),
        "{broken",
        java.nio.charset.StandardCharsets.UTF_8);

    IllegalStateException entriesFailure =
        assertThrows(
            IllegalStateException.class,
            () -> RegressionSeedCatalog.entries(tempDirectory, JazzerHarness.cliRequest()));
    String entriesFailureMessage = java.util.Objects.requireNonNull(entriesFailure.getMessage());
    assertTrue(entriesFailureMessage.contains("Committed regression metadata is unreadable:"));

    IllegalStateException orphanFailure =
        assertThrows(
            IllegalStateException.class,
            () -> RegressionSeedCatalog.orphanedInputs(tempDirectory, JazzerHarness.cliRequest()));
    String orphanFailureMessage = java.util.Objects.requireNonNull(orphanFailure.getMessage());
    assertTrue(orphanFailureMessage.contains("Committed regression metadata is invalid:"));
  }

  @Test
  void metadata_constructor_normalizes_relative_paths_and_rejects_invalid_shapes() {
    RegressionSeedMetadata metadata =
        new RegressionSeedMetadata(
            " cli-request ",
            " src/fuzz/resources/../resources/basic_valid.json ",
            " basic valid request ",
            new ReplayExpectation(
                ReplayOutcomeKind.SUCCESS,
                ReplayOutcome.SUCCESS_MESSAGE,
                new UnparsedCliRequestReplayDetails()));

    assertEquals("src/fuzz/resources/basic_valid.json", metadata.inputPath());
    assertEquals("basic valid request", metadata.coverageIntent());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedMetadata(
                " ", "relative.json", metadata.coverageIntent(), metadata.expectation()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedMetadata(
                "cli-request",
                Path.of("/tmp/absolute.json").toString(),
                metadata.coverageIntent(),
                metadata.expectation()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedMetadata(
                "cli-request", ".", metadata.coverageIntent(), metadata.expectation()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegressionSeedMetadata("cli-request", "seed.json", " ", metadata.expectation()));
  }

  @Test
  void committedSeedsDoNotReuseIdenticalRawInputBytesAcrossTheCorpus() throws IOException {
    Set<String> seenDigests = new HashSet<>();
    for (RegressionSeedCatalogEntry entry : RegressionSeedCatalog.entries(PROJECT_DIRECTORY)) {
      assertTrue(
          seenDigests.add(entry.sha256()),
          "committed seed bytes must be unique across the corpus: " + entry.inputPath());
    }
  }
}
