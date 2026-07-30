package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.jazzer.support.JazzerTestFixturePaths;
import dev.erst.fingrind.jazzer.support.JazzerTestProjectRoot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
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
      for (Path metadataPath : RegressionSeedPaths.metadataPaths(PROJECT_DIRECTORY, harness)) {
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
                    return RegressionSeedPaths.orphanedInputs(PROJECT_DIRECTORY, harness).stream();
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
        List.of(), RegressionSeedPaths.metadataPaths(tempDirectory, JazzerHarness.cliRequest()));
    assertEquals(
        List.of(), RegressionSeedPaths.inputPaths(tempDirectory, JazzerHarness.cliRequest()));
    assertEquals(
        List.of(), RegressionSeedPaths.orphanedInputs(tempDirectory, JazzerHarness.cliRequest()));

    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(tempDirectory);
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(tempDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);
    Path orphanInput = inputDirectory.resolve("orphan.json");
    Files.writeString(orphanInput, JazzerReplayRequestFixtures.basicValidRequest());

    Path canonicalOrphanInput =
        JazzerHarness.cliRequest()
            .inputDirectory(JazzerTestFixturePaths.canonicalExistingDirectory(tempDirectory))
            .resolve("orphan.json");
    assertEquals(
        List.of(canonicalOrphanInput),
        RegressionSeedPaths.orphanedInputs(tempDirectory, JazzerHarness.cliRequest()));
  }

  @Test
  void
      repository_path_admission_rejects_non_directories_and_preserves_concurrent_creation_evidence()
          throws Exception {
    Path nonDirectoryProjectRoot = tempDirectory.resolve("not-a-project-directory");
    Files.writeString(nonDirectoryProjectRoot, "not a directory");
    assertThrows(
        IOException.class,
        () ->
            RegressionSeedRepositoryPathAdmission.canonicalProjectDirectory(
                nonDirectoryProjectRoot));

    Path projectRoot = tempDirectory.resolve("project-root");
    Path outsideDirectory = tempDirectory.resolve("outside-regression-corpus");
    Files.createDirectory(projectRoot);
    Files.createDirectory(outsideDirectory);
    assertThrows(
        IOException.class,
        () ->
            RegressionSeedRepositoryPathAdmission.createOrRequireRealDirectoryTree(
                projectRoot, outsideDirectory));

    Path racedDirectory = tempDirectory.resolve("raced-directory");
    Files.writeString(racedDirectory, "concurrent file collision");
    FileAlreadyExistsException racedCreation =
        new FileAlreadyExistsException(racedDirectory.toString());
    IOException admissionFailure =
        assertThrows(
            IOException.class,
            () ->
                RegressionSeedRepositoryPathAdmission.requireRealDirectoryAfterConcurrentCreation(
                    racedDirectory, racedCreation));
    assertEquals(List.of(racedCreation), List.of(admissionFailure.getSuppressed()));

    Path concurrentDirectory = tempDirectory.resolve("concurrent-directory");
    RegressionSeedRepositoryPathAdmission.createOrRequireRealDirectory(
        concurrentDirectory,
        directory -> {
          Files.createDirectory(directory);
          throw new FileAlreadyExistsException(directory.toString());
        });
    assertTrue(Files.isDirectory(concurrentDirectory));

    Path concurrentRealDirectory = tempDirectory.resolve("concurrent-real-directory");
    Files.createDirectory(concurrentRealDirectory);
    RegressionSeedRepositoryPathAdmission.requireRealDirectoryAfterConcurrentCreation(
        concurrentRealDirectory,
        new FileAlreadyExistsException(concurrentRealDirectory.toString()));
  }

  @Test
  void catalog_helpers_refuse_symbolic_link_entries_and_nonconforming_discovered_artifacts()
      throws Exception {
    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(tempDirectory);
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(tempDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);
    Path regularTarget = tempDirectory.resolve("regular-target.json");
    Files.writeString(regularTarget, JazzerReplayRequestFixtures.basicValidRequest());
    createSymbolicLinkOrSkip(inputDirectory.resolve("linked-input.json"), regularTarget);
    createSymbolicLinkOrSkip(metadataDirectory.resolve("linked-metadata.json"), regularTarget);

    assertThrows(
        IOException.class,
        () -> RegressionSeedPaths.inputPaths(tempDirectory, JazzerHarness.cliRequest()));
    assertThrows(
        IOException.class,
        () -> RegressionSeedPaths.metadataPaths(tempDirectory, JazzerHarness.cliRequest()));

    Path plainFile = tempDirectory.resolve("plain-file");
    Files.writeString(plainFile, "not a directory");
    assertThrows(
        IOException.class,
        () -> RegressionSeedPaths.requireExistingRealDirectory(plainFile, "input"));
    assertThrows(
        IOException.class,
        () -> RegressionSeedPaths.requireExistingRegularFile(tempDirectory, "metadata"));
  }

  @Test
  void metadata_inspection_reports_non_regular_metadata_and_missing_input_ancestors()
      throws Exception {
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(tempDirectory, JazzerHarness.cliRequest());
    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(tempDirectory);
    Files.createDirectories(metadataDirectory);
    Files.createDirectories(inputDirectory);

    Path metadataDirectoryEntry = metadataDirectory.resolve("directory-metadata.json");
    Files.createDirectory(metadataDirectoryEntry);
    RegressionSeedMetadataInspection metadataDirectoryInspection =
        RegressionSeedMetadataInspector.inspectMetadataPath(
            tempDirectory, JazzerHarness.cliRequest(), metadataDirectoryEntry);
    assertEquals(
        "metadata-read-failure",
        java.util.Objects.requireNonNull(metadataDirectoryInspection.problem()).problemKind());

    Path missingInput = inputDirectory.resolve("missing-parent").resolve("input.json");
    Path metadataPath = metadataDirectory.resolve("missing-parent.json");
    JazzerJson.write(
        metadataPath,
        new RegressionSeedMetadata(
            JazzerHarness.cliRequest().key(),
            tempDirectory.relativize(missingInput).toString(),
            "missing input parent",
            new ReplayExpectation(
                ReplayOutcomeKind.SUCCESS,
                ReplayOutcome.SUCCESS_MESSAGE,
                new UnparsedCliRequestReplayDetails())));
    RegressionSeedMetadataInspection missingInputInspection =
        RegressionSeedMetadataInspector.inspectMetadataPath(
            tempDirectory, JazzerHarness.cliRequest(), metadataPath);
    assertEquals(
        "input-missing",
        java.util.Objects.requireNonNull(missingInputInspection.problem()).problemKind());

    Path outsideInputDirectory = tempDirectory.resolve("outside-input-directory");
    Files.createDirectory(outsideInputDirectory);
    Path symlinkedInputParent = inputDirectory.resolve("symlinked-input-parent");
    createSymbolicLinkOrSkip(symlinkedInputParent, outsideInputDirectory);
    Path symlinkedInput = symlinkedInputParent.resolve("input.json");
    Path symlinkedMetadataPath = metadataDirectory.resolve("symlinked-input-parent.json");
    JazzerJson.write(
        symlinkedMetadataPath,
        new RegressionSeedMetadata(
            JazzerHarness.cliRequest().key(),
            tempDirectory.relativize(symlinkedInput).toString(),
            "symlinked input parent",
            new ReplayExpectation(
                ReplayOutcomeKind.SUCCESS,
                ReplayOutcome.SUCCESS_MESSAGE,
                new UnparsedCliRequestReplayDetails())));
    RegressionSeedMetadataInspection symlinkedInputInspection =
        RegressionSeedMetadataInspector.inspectMetadataPath(
            tempDirectory, JazzerHarness.cliRequest(), symlinkedMetadataPath);
    assertEquals(
        "input-not-regular-file",
        java.util.Objects.requireNonNull(symlinkedInputInspection.problem()).problemKind());
  }

  @Test
  void catalog_helpers_canonicalize_project_root_aliases_before_comparing_inputs()
      throws Exception {
    Path realProjectDirectory = tempDirectory.resolve("real-project");
    Files.createDirectory(realProjectDirectory);
    Path projectAlias = tempDirectory.resolve("project-alias");
    createSymbolicLinkOrSkip(projectAlias, realProjectDirectory);

    Path inputPath =
        JazzerHarness.cliRequest().inputDirectory(realProjectDirectory).resolve("basic.json");
    Files.createDirectories(inputPath.getParent());
    Files.writeString(inputPath, JazzerReplayRequestFixtures.basicValidRequest());
    Path metadataPath =
        RegressionSeedPaths.metadataDirectory(realProjectDirectory, JazzerHarness.cliRequest())
            .resolve("basic.json");
    Files.createDirectories(metadataPath.getParent());
    JazzerJson.write(
        metadataPath,
        new RegressionSeedMetadata(
            JazzerHarness.cliRequest().key(),
            realProjectDirectory.relativize(inputPath).toString(),
            "project alias canonicalization",
            JazzerReplayRunner.expectationFor(
                JazzerReplayRunner.replay(
                    JazzerHarness.cliRequest(), Files.readAllBytes(inputPath)))));

    assertEquals(
        List.of(), RegressionSeedPaths.orphanedInputs(projectAlias, JazzerHarness.cliRequest()));
  }

  @Test
  void catalog_helpers_refuse_symbolic_link_corpus_directories() throws Exception {
    Path outsideDirectory = tempDirectory.resolve("outside");
    Files.createDirectory(outsideDirectory);
    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(tempDirectory);
    Files.createDirectories(inputDirectory.getParent());
    createSymbolicLinkOrSkip(inputDirectory, outsideDirectory);

    IOException rejection =
        assertThrows(
            IOException.class,
            () -> RegressionSeedPaths.inputPaths(tempDirectory, JazzerHarness.cliRequest()));

    assertTrue(String.valueOf(rejection.getMessage()).contains("real non-symlink directory"));
  }

  @Test
  void catalog_helpers_refuse_symbolic_link_corpus_ancestors() throws Exception {
    Path outsideDirectory = tempDirectory.resolve("outside");
    Files.createDirectory(outsideDirectory);
    createSymbolicLinkOrSkip(tempDirectory.resolve("src"), outsideDirectory);

    IOException inputRejection =
        assertThrows(
            IOException.class,
            () -> RegressionSeedPaths.inputPaths(tempDirectory, JazzerHarness.cliRequest()));
    IOException metadataRejection =
        assertThrows(
            IOException.class,
            () -> RegressionSeedPaths.metadataPaths(tempDirectory, JazzerHarness.cliRequest()));

    assertTrue(String.valueOf(inputRejection.getMessage()).contains("real non-symlink directory"));
    assertTrue(
        String.valueOf(metadataRejection.getMessage()).contains("real non-symlink directory"));
    assertFalse(Files.exists(outsideDirectory.resolve("fuzz")));
  }

  @Test
  void strict_catalog_helpers_fail_fast_on_invalid_metadata() throws IOException {
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(tempDirectory, JazzerHarness.cliRequest());
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
            () -> RegressionSeedEntries.entries(tempDirectory, JazzerHarness.cliRequest()));
    String entriesFailureMessage = java.util.Objects.requireNonNull(entriesFailure.getMessage());
    assertTrue(entriesFailureMessage.contains("Committed regression metadata is unreadable:"));

    IllegalStateException orphanFailure =
        assertThrows(
            IllegalStateException.class,
            () -> RegressionSeedPaths.orphanedInputs(tempDirectory, JazzerHarness.cliRequest()));
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
    for (RegressionSeedCatalogEntry entry : RegressionSeedEntries.entries(PROJECT_DIRECTORY)) {
      assertTrue(
          seenDigests.add(entry.sha256()),
          "committed seed bytes must be unique across the corpus: " + entry.inputPath());
    }
  }

  private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
    try {
      Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException | IOException unsupported) {
      Assumptions.assumeTrue(
          false, "Symbolic-link refusal coverage requires local symbolic-link support.");
    }
  }
}
