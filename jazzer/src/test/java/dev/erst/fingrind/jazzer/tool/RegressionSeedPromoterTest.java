package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers promotion of ad hoc replay inputs into committed regression seeds. */
class RegressionSeedPromoterTest {
  @TempDir Path projectDirectory;

  @Test
  void promote_createsCommittedInputAndMetadata() throws Exception {
    Path sourceInput = projectDirectory.resolve("raw-input.json");
    Files.writeString(sourceInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);

    RegressionSeedPromotionResult result =
        RegressionSeedPromoter.promote(
            projectDirectory,
            JazzerHarness.cliRequest(),
            sourceInput,
            "valid_cli_request",
            "minimal valid cli request");

    assertEquals("cli-request", result.targetKey());
    assertTrue(Files.exists(result.committedInputPath()));
    assertTrue(Files.exists(result.metadataPath()));
    assertEquals("minimal valid cli request", result.coverageIntent());
    assertEquals(
        JazzerReplayRunner.expectationFor(
            JazzerReplayRunner.replay(
                JazzerHarness.cliRequest(), Files.readAllBytes(result.committedInputPath()))),
        result.expectation());

    RegressionSeedMetadata metadata =
        JazzerJson.read(result.metadataPath(), RegressionSeedMetadata.class);
    assertEquals("cli-request", metadata.targetKey());
    assertEquals("minimal valid cli request", metadata.coverageIntent());
    assertEquals(result.expectation(), metadata.expectation());
    assertEquals(
        result.committedInputPath(),
        metadata.inputPath(projectDirectory).toAbsolutePath().normalize());
  }

  @Test
  void promote_rejectsInvalidSeedNamesAndDuplicateCommittedBytes() throws Exception {
    Path existingSource = projectDirectory.resolve("existing.json");
    Files.writeString(existingSource, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    RegressionSeedPromoter.promote(
        projectDirectory,
        JazzerHarness.cliRequest(),
        existingSource,
        "existing_seed",
        "existing valid cli request");

    IllegalArgumentException invalidSeedName =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.cliRequest(),
                    existingSource,
                    "invalid-seed-name",
                    "invalid"));
    assertTrue(String.valueOf(invalidSeedName.getMessage()).contains("lower_snake_case"));
    assertTrue(String.valueOf(invalidSeedName.getMessage()).contains("Try: invalid_seed_name"));

    Path duplicateSource = projectDirectory.resolve("duplicate.json");
    Files.writeString(duplicateSource, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    Path secondDuplicateInput =
        JazzerHarness.postingWorkflow()
            .inputDirectory(projectDirectory)
            .resolve("duplicate_peer.json");
    Files.createDirectories(secondDuplicateInput.getParent());
    Files.writeString(secondDuplicateInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    IllegalArgumentException duplicateBytes =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.postingWorkflow(),
                    duplicateSource,
                    "duplicate_seed",
                    "duplicate bytes"));
    assertTrue(
        String.valueOf(duplicateBytes.getMessage())
            .contains("Committed seed content already exists"));
    assertTrue(String.valueOf(duplicateBytes.getMessage()).contains("existing_seed.json"));
    assertTrue(String.valueOf(duplicateBytes.getMessage()).contains("duplicate_peer.json"));
    assertFalse(
        Files.exists(
            projectDirectory.resolve(
                "src/fuzz/resources/dev/erst/fingrind/cli/PostingWorkflowFuzzTestInputs/"
                    + "exercisePostingWorkflow/duplicate_seed.json")));

    Path uniqueSource = projectDirectory.resolve("unique.json");
    Files.writeString(uniqueSource, "{\"unique\":true}", UTF_8);
    IllegalArgumentException duplicateCoverageIntent =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.postingWorkflow(),
                    uniqueSource,
                    "duplicate_intent_seed",
                    "existing valid cli request"));
    assertTrue(
        String.valueOf(duplicateCoverageIntent.getMessage())
            .contains("Committed seed coverage intent already exists"));
    assertTrue(String.valueOf(duplicateCoverageIntent.getMessage()).contains("existing_seed.json"));
  }

  @Test
  void promote_rejects_unexpected_failure_replay_outcomes() throws Exception {
    Path sourceInput = projectDirectory.resolve("raw-input.json");
    Files.writeString(sourceInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);

    IllegalArgumentException unexpectedFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.cliRequest(),
                    sourceInput,
                    "buggy_seed",
                    "buggy seed",
                    (_harness, _input) ->
                        new ReplayOutcome.UnexpectedFailure(
                            "cli-request",
                            "AssertionError",
                            "boom",
                            "stack",
                            new UnparsedCliRequestReplayDetails())));

    assertTrue(String.valueOf(unexpectedFailure.getMessage()).contains("unexpected-failure"));
    assertFalse(Files.exists(projectDirectory.resolve("src/fuzz/resources")));
  }

  @Test
  void normalizedSeedNameSuggestion_covers_empty_and_non_alphanumeric_leading_cases() {
    assertEquals("seed", RegressionSeedPromoter.normalizedSeedNameSuggestion("   "));
    assertEquals(
        "123_bad_seed", RegressionSeedPromoter.normalizedSeedNameSuggestion("___123 Bad Seed"));
  }

  @Test
  void promote_defaults_extension_and_rejects_non_regular_or_reserved_paths() throws Exception {
    Path sourceInput = projectDirectory.resolve("raw-input");
    Files.writeString(sourceInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);

    RegressionSeedPromotionResult result =
        RegressionSeedPromoter.promote(
            projectDirectory,
            JazzerHarness.cliRequest(),
            sourceInput,
            "extensionless_seed",
            "extensionless valid cli request");
    assertEquals("extensionless_seed.bin", result.committedInputPath().getFileName().toString());

    Path trailingDotSource = projectDirectory.resolve("trailing.");
    Files.writeString(trailingDotSource, "{\"distinct\":\"trailing-dot\"}", UTF_8);
    RegressionSeedPromotionResult trailingDotResult =
        RegressionSeedPromoter.promote(
            projectDirectory,
            JazzerHarness.cliRequest(),
            trailingDotSource,
            "trailing_dot_seed",
            "trailing-dot source input");
    assertEquals(
        "trailing_dot_seed.bin", trailingDotResult.committedInputPath().getFileName().toString());

    IllegalArgumentException nonRegularFile =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.cliRequest(),
                    projectDirectory,
                    "directory_seed",
                    "directory input"));
    assertTrue(String.valueOf(nonRegularFile.getMessage()).contains("existing regular file"));

    Path collidingInput =
        JazzerHarness.postingWorkflow()
            .inputDirectory(projectDirectory)
            .resolve("reserved_seed.json");
    Files.createDirectories(collidingInput.getParent());
    Files.writeString(collidingInput, "{\"other\":true}", UTF_8);
    Path distinctSource = projectDirectory.resolve("distinct.json");
    Files.writeString(distinctSource, "{\"distinct\":\"posting-workflow\"}", UTF_8);
    IllegalArgumentException existingInputPath =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.postingWorkflow(),
                    distinctSource,
                    "reserved_seed",
                    "reserved input path"));
    assertTrue(
        String.valueOf(existingInputPath.getMessage())
            .contains("Committed seed input path already exists"));

    Path metadataPath =
        RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.ledgerPlanRequest())
            .resolve("reserved_metadata.json");
    Files.createDirectories(metadataPath.getParent());
    Path existingLedgerPlanInput =
        JazzerHarness.ledgerPlanRequest()
            .inputDirectory(projectDirectory)
            .resolve("existing_reserved_metadata_input.json");
    Files.createDirectories(existingLedgerPlanInput.getParent());
    Files.writeString(existingLedgerPlanInput, "{\"distinct\":\"existing-ledger-plan\"}", UTF_8);
    JazzerJson.write(
        metadataPath,
        new RegressionSeedMetadata(
            JazzerHarness.ledgerPlanRequest().key(),
            projectDirectory
                .toAbsolutePath()
                .normalize()
                .relativize(existingLedgerPlanInput.toAbsolutePath().normalize())
                .toString(),
            "existing reserved metadata input",
            JazzerReplayRunner.expectationFor(
                JazzerReplayRunner.replay(
                    JazzerHarness.ledgerPlanRequest(),
                    Files.readAllBytes(existingLedgerPlanInput)))));
    Path planSource = projectDirectory.resolve("plan.json");
    Files.writeString(planSource, "{\"distinct\":\"ledger-plan-request\"}", UTF_8);
    IllegalArgumentException existingMetadataPath =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.ledgerPlanRequest(),
                    planSource,
                    "reserved_metadata",
                    "reserved metadata path"));
    assertTrue(
        String.valueOf(existingMetadataPath.getMessage())
            .contains("Committed seed metadata path already exists"));
  }

  @Test
  void promote_retains_partial_committed_artifacts_when_metadata_write_fails() throws Exception {
    Path sourceInput = projectDirectory.resolve("raw-input.json");
    Files.writeString(sourceInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);

    RegressionSeedPromotionRetainedArtifactsException metadataWriteFailure =
        assertThrows(
            RegressionSeedPromotionRetainedArtifactsException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.cliRequest(),
                    sourceInput,
                    "metadata_write_failure",
                    "metadata write failure",
                    JazzerReplayRunner::replay,
                    (metadataPath, _metadata) -> {
                      Files.writeString(metadataPath, "{\"partial\":", UTF_8);
                      throw new IOException("metadata boom");
                    }));

    Path committedInputPath =
        JazzerHarness.cliRequest()
            .inputDirectory(projectDirectory)
            .resolve("metadata_write_failure.json");
    Path metadataPath =
        RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest())
            .resolve("metadata_write_failure.json");
    Path normalizedCommittedInputPath = committedInputPath.toAbsolutePath().normalize();
    Path normalizedMetadataPath = metadataPath.toAbsolutePath().normalize();
    assertEquals("metadata boom", metadataWriteFailure.getCause().getMessage());
    assertTrue(metadataWriteFailure.getMessage().contains("metadata boom"));
    assertTrue(metadataWriteFailure.getMessage().contains("jazzer/bin/seed-audit"));
    assertTrue(metadataWriteFailure.getMessage().contains("Do not retry or clean them in place"));
    assertEquals(
        normalizedCommittedInputPath, metadataWriteFailure.retention().committedInputPath());
    assertEquals(normalizedMetadataPath, metadataWriteFailure.retention().metadataPath());
    assertEquals(
        List.of(normalizedCommittedInputPath, normalizedMetadataPath),
        metadataWriteFailure.retention().retainedArtifactPaths());
    assertTrue(Files.exists(committedInputPath));
    assertEquals("{\"partial\":", Files.readString(metadataPath, UTF_8));

    RegressionSeedAuditReport audit =
        RegressionSeedAuditor.audit(projectDirectory, JazzerHarness.cliRequest());
    assertEquals(List.of(normalizedCommittedInputPath), audit.orphanedInputPaths());
    assertEquals(1, audit.integrityProblemCount());
    assertEquals("metadata-read-failure", audit.integrityProblems().getFirst().problemKind());
    assertEquals(normalizedMetadataPath, audit.integrityProblems().getFirst().metadataPath());
  }

  @Test
  void retained_artifact_contract_normalizes_and_rejects_ambiguous_candidates() {
    Path committedInputPath = projectDirectory.resolve("corpus").resolve("input.json");
    Path metadataPath = projectDirectory.resolve("metadata").resolve("input.json");
    RegressionSeedPromotionRetention retention =
        new RegressionSeedPromotionRetention(
            committedInputPath,
            metadataPath,
            List.of(committedInputPath.getParent().resolve(".").resolve("input.json")));

    assertEquals(
        List.of(committedInputPath.toAbsolutePath().normalize()),
        retention.retainedArtifactPaths());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedPromotionRetention(
                committedInputPath,
                metadataPath,
                List.of(committedInputPath, committedInputPath)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedPromotionRetention(
                committedInputPath, metadataPath, List.of(projectDirectory.resolve("unrelated"))));
  }

  @Test
  void retained_artifact_exception_preserves_paths_across_java_serialization() throws Exception {
    RegressionSeedPromotionRetention retention =
        new RegressionSeedPromotionRetention(
            projectDirectory.resolve("corpus/input.json"),
            projectDirectory.resolve("metadata/input.json"),
            List.of(projectDirectory.resolve("corpus/input.json")));

    RegressionSeedPromotionRetainedArtifactsException restored =
        roundTrip(
            new RegressionSeedPromotionRetainedArtifactsException(
                retention, new IOException("metadata write failed")),
            RegressionSeedPromotionRetainedArtifactsException.class);

    assertEquals(retention, restored.retention());
    assertEquals("metadata write failed", restored.getCause().getMessage());
  }

  @Test
  void promote_rejects_missing_source_inputs() {
    Path missingSource = projectDirectory.resolve("missing.json");
    IllegalArgumentException missingInput =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.cliRequest(),
                    missingSource,
                    "missing_seed",
                    "missing source input"));
    assertTrue(String.valueOf(missingInput.getMessage()).contains("existing regular file"));
  }

  @Test
  void promote_refuses_symbolic_link_source_inputs() throws Exception {
    Path actualSource = projectDirectory.resolve("actual-source.json");
    Files.writeString(actualSource, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    Path linkedSource = projectDirectory.resolve("linked-source.json");
    createSymbolicLinkOrSkip(linkedSource, actualSource);

    IllegalArgumentException rejection =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.cliRequest(),
                    linkedSource,
                    "linked_source",
                    "symbolic link source"));

    assertTrue(String.valueOf(rejection.getMessage()).contains("existing regular file"));
    assertFalse(Files.exists(JazzerHarness.cliRequest().inputDirectory(projectDirectory)));
  }

  @Test
  void promote_refuses_symlinked_corpus_ancestors_without_writing_outside_the_project()
      throws Exception {
    Path sourceInput = projectDirectory.resolve("raw-input.json");
    Files.writeString(sourceInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    Path outsideProject = projectDirectory.resolve("outside-project");
    Files.createDirectory(outsideProject);
    createSymbolicLinkOrSkip(projectDirectory.resolve("src"), outsideProject);

    IOException rejection =
        assertThrows(
            IOException.class,
            () ->
                RegressionSeedPromoter.promote(
                    projectDirectory,
                    JazzerHarness.cliRequest(),
                    sourceInput,
                    "symlinked_corpus",
                    "symlinked corpus ancestor"));

    assertTrue(String.valueOf(rejection.getMessage()).contains("real non-symlink directory"));
    assertFalse(Files.exists(outsideProject.resolve("fuzz")));
  }

  @Test
  void promote_overload_withInjectedReplayExecutor_uses_default_metadata_writer() throws Exception {
    Path sourceInput = projectDirectory.resolve("overload.json");
    Files.writeString(sourceInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);

    RegressionSeedPromotionResult result =
        RegressionSeedPromoter.promote(
            projectDirectory,
            JazzerHarness.cliRequest(),
            sourceInput,
            "injected_executor_seed",
            "injected executor path",
            (_harness, _input) ->
                new ReplayOutcome.ExpectedInvalid(
                    "cli-request",
                    "Injected",
                    "Injected expected invalid replay outcome.",
                    new UnparsedCliRequestReplayDetails()));

    assertEquals("cli-request", result.targetKey());
    assertEquals(ReplayOutcomeKind.EXPECTED_INVALID, result.expectation().outcomeKind());
    assertTrue(Files.exists(result.metadataPath()));
  }

  private static <T extends Throwable> T roundTrip(T exception, Class<T> expectedType)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return expectedType.cast(input.readObject());
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
