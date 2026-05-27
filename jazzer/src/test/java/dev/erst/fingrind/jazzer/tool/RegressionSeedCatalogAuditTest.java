package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers committed-seed audit reporting and duplicate-content detection. */
class RegressionSeedCatalogAuditTest {
  @TempDir Path projectDirectory;

  @Test
  void audit_reportsPerTargetCountsAndCrossHarnessDuplicateContent() throws Exception {
    writeCommittedSeed(
        JazzerHarness.cliRequest(),
        "duplicate_cli",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "cli duplicate");
    writeCommittedSeed(
        JazzerHarness.postingWorkflow(),
        "duplicate_posting",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "posting duplicate");

    RegressionSeedAuditReport fullAudit = RegressionSeedAuditor.audit(projectDirectory);
    assertEquals(2, fullAudit.totalSeedCount());
    assertEquals(1, fullAudit.uniqueInputContentCount());
    assertEquals(0, fullAudit.orphanedInputCount());
    assertEquals(0, fullAudit.unexpectedFailureSeedCount());
    assertEquals(0, fullAudit.integrityProblemCount());
    assertEquals(2, fullAudit.targets().size());
    assertEquals(1, fullAudit.duplicateContentGroups().size());
    assertTrue(
        fullAudit.duplicateContentGroups().getFirst().inputPaths().stream()
            .anyMatch(path -> "duplicate_cli.json".equals(path.getFileName().toString())));

    RegressionSeedAuditReport cliAudit =
        RegressionSeedAuditor.audit(projectDirectory, JazzerHarness.cliRequest());
    assertEquals(1, cliAudit.totalSeedCount());
    assertEquals(1, cliAudit.targets().size());
    assertEquals("cli-request", cliAudit.targets().getFirst().targetKey());
    assertEquals(1, cliAudit.duplicateContentGroups().size());
  }

  @Test
  void audit_scopes_duplicate_groups_to_the_requested_harness() throws Exception {
    writeCommittedSeed(
        JazzerHarness.postingWorkflow(),
        "duplicate_posting",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "posting duplicate");
    writeCommittedSeed(
        JazzerHarness.sqliteBookRoundTrip(),
        "duplicate_sqlite",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "sqlite duplicate");
    writeCommittedSeed(
        JazzerHarness.cliRequest(),
        "unique_cli",
        JazzerReplayRequestFixtures.invalidDuplicateIdempotencyKeyRequest(),
        "unique cli seed");

    RegressionSeedAuditReport cliAudit =
        RegressionSeedAuditor.audit(projectDirectory, JazzerHarness.cliRequest());
    assertEquals(1, cliAudit.totalSeedCount());
    assertEquals(0, cliAudit.duplicateContentGroups().size());
  }

  @Test
  void duplicateContentGroups_reports_duplicate_raw_input_bytes_across_harnesses()
      throws Exception {
    writeCommittedSeed(
        JazzerHarness.cliRequest(),
        "duplicate_cli",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "cli duplicate");
    writeCommittedSeed(
        JazzerHarness.postingWorkflow(),
        "duplicate_posting",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "posting duplicate");
    writeCommittedSeed(
        JazzerHarness.sqliteBookRoundTrip(),
        "unique_sqlite",
        JazzerReplayRequestFixtures.invalidDuplicateIdempotencyKeyRequest(),
        "unique sqlite");

    List<RegressionSeedDuplicateContent> duplicateGroups =
        RegressionSeedDigests.duplicateContentGroups(projectDirectory);

    assertEquals(1, duplicateGroups.size());
    assertEquals(2, duplicateGroups.getFirst().inputPaths().size());
    assertTrue(
        duplicateGroups.getFirst().inputPaths().stream()
            .anyMatch(path -> "duplicate_cli.json".equals(path.getFileName().toString())));
    assertTrue(
        duplicateGroups.getFirst().inputPaths().stream()
            .anyMatch(path -> "duplicate_posting.json".equals(path.getFileName().toString())));
  }

  @Test
  void audit_reports_orphaned_inputs_and_unexpected_failure_expectations() throws Exception {
    writeCommittedSeed(
        JazzerHarness.cliRequest(),
        "buggy_seed",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "unexpected failure seed",
        new ReplayExpectation(
            ReplayOutcomeKind.UNEXPECTED_FAILURE, "boom", new UnparsedCliRequestReplayDetails()));

    Path postingInputDirectory = JazzerHarness.postingWorkflow().inputDirectory(projectDirectory);
    Files.createDirectories(postingInputDirectory);
    Path orphanInput = postingInputDirectory.resolve("orphan.json");
    Files.writeString(
        orphanInput, JazzerReplayRequestFixtures.invalidDuplicateIdempotencyKeyRequest(), UTF_8);

    RegressionSeedAuditReport fullAudit = RegressionSeedAuditor.audit(projectDirectory);
    assertEquals(1, fullAudit.totalSeedCount());
    assertEquals(1, fullAudit.uniqueInputContentCount());
    assertEquals(1, fullAudit.orphanedInputCount());
    assertEquals(1, fullAudit.unexpectedFailureSeedCount());
    assertEquals(0, fullAudit.integrityProblemCount());
    assertEquals(List.of(orphanInput.toAbsolutePath().normalize()), fullAudit.orphanedInputPaths());
    assertEquals(1, fullAudit.unexpectedFailureSeeds().size());
    assertEquals(
        "buggy_seed.json",
        fullAudit.unexpectedFailureSeeds().getFirst().inputPath().getFileName().toString());
    assertEquals(0, fullAudit.duplicateContentGroups().size());
  }

  @Test
  void audit_helpers_cover_all_input_paths_and_constructor_invariants() throws Exception {
    Path cliInputDirectory = JazzerHarness.cliRequest().inputDirectory(projectDirectory);
    Files.createDirectories(cliInputDirectory);
    Path orphanInput = cliInputDirectory.resolve("orphan.bin");
    Files.writeString(orphanInput, "raw", UTF_8);

    assertEquals(
        List.of(orphanInput.toAbsolutePath().normalize()),
        RegressionSeedPaths.allInputPaths(projectDirectory));

    Path normalizedPath = orphanInput.toAbsolutePath().normalize();
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegressionSeedDuplicateContent("sha256", List.of(normalizedPath)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedTargetAudit(
                "cli-request", 2, List.of(), List.of(), List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedAuditReport(
                1, 2, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedAuditReport(
                1, 1, 1, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedAuditReport(
                1, 1, 0, 1, 0, List.of(), List.of(), List.of(), List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedAuditReport(
                1, 1, 0, 0, 1, List.of(), List.of(), List.of(), List.of(), List.of()));

    IllegalStateException unavailableDigest =
        assertThrows(
            IllegalStateException.class,
            () ->
                RegressionSeedDigests.sha256Hex(
                    "raw".getBytes(UTF_8),
                    () -> {
                      throw new NoSuchAlgorithmException("missing");
                    }));
    assertEquals("SHA-256 digest is unavailable in this JVM.", unavailableDigest.getMessage());
  }

  @Test
  void audit_returns_empty_report_for_harnesses_without_committed_inputs() throws Exception {
    RegressionSeedAuditReport emptyAudit =
        RegressionSeedAuditor.audit(projectDirectory, JazzerHarness.cliRequest());

    assertEquals(0, emptyAudit.totalSeedCount());
    assertEquals(0, emptyAudit.uniqueInputContentCount());
    assertEquals(0, emptyAudit.targets().size());
    assertEquals(0, emptyAudit.duplicateContentGroups().size());
    assertEquals(0, emptyAudit.integrityProblemCount());
  }

  @Test
  void audit_reports_malformed_metadata_references_and_json_seed_bodies() throws Exception {
    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(projectDirectory);
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);

    Path escapedInput = projectDirectory.resolve("tmp").resolve("escaped.json");
    Files.createDirectories(escapedInput.getParent());
    Files.writeString(escapedInput, "{\"escaped\":true}", UTF_8);
    JazzerJson.write(
        metadataDirectory.resolve("escaped.json"),
        new RegressionSeedMetadata(
            "cli-request",
            projectDirectory.relativize(escapedInput.toAbsolutePath().normalize()).toString(),
            "escaped path",
            JazzerReplayRunner.expectationFor(
                JazzerReplayRunner.replay(
                    JazzerHarness.cliRequest(),
                    JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8)))));

    Path malformedInput = inputDirectory.resolve("malformed.json");
    Files.writeString(malformedInput, "{\"valid\": true}\n{\"extra\": false}\n", UTF_8);
    JazzerJson.write(
        metadataDirectory.resolve("malformed.json"),
        new RegressionSeedMetadata(
            "cli-request",
            projectDirectory.relativize(malformedInput.toAbsolutePath().normalize()).toString(),
            "malformed json",
            JazzerReplayRunner.expectationFor(
                JazzerReplayRunner.replay(
                    JazzerHarness.cliRequest(),
                    JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8)))));

    RegressionSeedAuditReport audit =
        RegressionSeedAuditor.audit(projectDirectory, JazzerHarness.cliRequest());

    assertEquals(0, audit.totalSeedCount());
    assertEquals(0, audit.uniqueInputContentCount());
    assertEquals(0, audit.orphanedInputCount());
    assertEquals(2, audit.integrityProblemCount());
    assertEquals(2, audit.targets().getFirst().integrityProblems().size());
    assertTrue(
        audit.integrityProblems().stream()
            .anyMatch(problem -> "input-outside-harness".equals(problem.problemKind())));
    assertTrue(
        audit.integrityProblems().stream()
            .anyMatch(problem -> "input-json-malformed".equals(problem.problemKind())));
  }

  @Test
  void audit_reports_unreadable_metadata_and_non_file_input_references() throws Exception {
    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(projectDirectory);
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);

    Files.writeString(metadataDirectory.resolve("broken.json"), "{not-json", UTF_8);

    Path directoryInput = inputDirectory.resolve("directory-input");
    Files.createDirectories(directoryInput);
    JazzerJson.write(
        metadataDirectory.resolve("directory-input.json"),
        new RegressionSeedMetadata(
            "cli-request",
            projectDirectory.relativize(directoryInput.toAbsolutePath().normalize()).toString(),
            "directory input",
            JazzerReplayRunner.expectationFor(
                JazzerReplayRunner.replay(
                    JazzerHarness.cliRequest(),
                    JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8)))));

    RegressionSeedAuditReport audit =
        RegressionSeedAuditor.audit(projectDirectory, JazzerHarness.cliRequest());

    assertEquals(0, audit.totalSeedCount());
    assertEquals(2, audit.integrityProblemCount());
    assertTrue(
        audit.integrityProblems().stream()
            .anyMatch(problem -> "metadata-read-failure".equals(problem.problemKind())));
    assertTrue(
        audit.integrityProblems().stream()
            .anyMatch(problem -> "input-not-regular-file".equals(problem.problemKind())));
    assertTrue(
        audit.integrityProblems().stream()
            .anyMatch(
                problem ->
                    "metadata-read-failure".equals(problem.problemKind())
                        && problem.inputPath() == null));
  }

  @Test
  void audit_reports_target_mismatch_missing_inputs_and_unreadable_inputs() throws Exception {
    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(projectDirectory);
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);

    ReplayExpectation validExpectation =
        JazzerReplayRunner.expectationFor(
            JazzerReplayRunner.replay(
                JazzerHarness.cliRequest(),
                JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8)));

    Path mismatchedInput = inputDirectory.resolve("target-mismatch.json");
    Files.writeString(mismatchedInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    JazzerJson.write(
        metadataDirectory.resolve("target-mismatch.json"),
        new RegressionSeedMetadata(
            "posting-workflow",
            projectDirectory.relativize(mismatchedInput.toAbsolutePath().normalize()).toString(),
            "target mismatch",
            validExpectation));

    Path missingInput = inputDirectory.resolve("missing.json");
    JazzerJson.write(
        metadataDirectory.resolve("missing.json"),
        new RegressionSeedMetadata(
            "cli-request",
            projectDirectory.relativize(missingInput.toAbsolutePath().normalize()).toString(),
            "missing input",
            validExpectation));

    Path unreadableInput = inputDirectory.resolve("unreadable.json");
    Files.writeString(unreadableInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    Assumptions.assumeTrue(
        Files.getFileStore(unreadableInput).supportsFileAttributeView("posix"),
        "Unreadable-input coverage requires POSIX file permissions.");
    Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(unreadableInput);
    JazzerJson.write(
        metadataDirectory.resolve("unreadable.json"),
        new RegressionSeedMetadata(
            "cli-request",
            projectDirectory.relativize(unreadableInput.toAbsolutePath().normalize()).toString(),
            "unreadable input",
            validExpectation));
    try {
      Files.setPosixFilePermissions(unreadableInput, Set.of());

      RegressionSeedAuditReport audit =
          RegressionSeedAuditor.audit(projectDirectory, JazzerHarness.cliRequest());

      assertEquals(0, audit.totalSeedCount());
      assertEquals(0, audit.orphanedInputCount());
      assertEquals(3, audit.integrityProblemCount());
      assertTrue(
          audit.integrityProblems().stream()
              .anyMatch(problem -> "target-mismatch".equals(problem.problemKind())));
      assertTrue(
          audit.integrityProblems().stream()
              .anyMatch(problem -> "input-missing".equals(problem.problemKind())));
      assertTrue(
          audit.integrityProblems().stream()
              .anyMatch(problem -> "input-read-failure".equals(problem.problemKind())));
    } finally {
      Files.setPosixFilePermissions(unreadableInput, originalPermissions);
    }
  }

  @Test
  void entries_accept_non_json_seed_inputs_without_trying_to_parse_them() throws Exception {
    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(projectDirectory);
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);

    Path binaryInput = inputDirectory.resolve("valid.bin");
    Files.writeString(binaryInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    JazzerJson.write(
        metadataDirectory.resolve("valid.json"),
        new RegressionSeedMetadata(
            "cli-request",
            projectDirectory.relativize(binaryInput.toAbsolutePath().normalize()).toString(),
            "binary extension seed",
            JazzerReplayRunner.expectationFor(
                JazzerReplayRunner.replay(
                    JazzerHarness.cliRequest(),
                    JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8)))));

    List<RegressionSeedCatalogEntry> entries =
        RegressionSeedEntries.entries(projectDirectory, JazzerHarness.cliRequest());

    assertEquals(1, entries.size());
    assertEquals(binaryInput.toAbsolutePath().normalize(), entries.getFirst().inputPath());
  }

  @Test
  void audit_reports_unreadable_non_json_inputs_as_read_failures() throws Exception {
    Path inputDirectory = JazzerHarness.cliRequest().inputDirectory(projectDirectory);
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);

    Path unreadableInput = inputDirectory.resolve("unreadable.bin");
    Files.writeString(unreadableInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    Assumptions.assumeTrue(
        Files.getFileStore(unreadableInput).supportsFileAttributeView("posix"),
        "Unreadable-input coverage requires POSIX file permissions.");
    Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(unreadableInput);
    JazzerJson.write(
        metadataDirectory.resolve("unreadable-bin.json"),
        new RegressionSeedMetadata(
            "cli-request",
            projectDirectory.relativize(unreadableInput.toAbsolutePath().normalize()).toString(),
            "unreadable binary input",
            JazzerReplayRunner.expectationFor(
                JazzerReplayRunner.replay(
                    JazzerHarness.cliRequest(),
                    JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8)))));
    try {
      Files.setPosixFilePermissions(unreadableInput, Set.of());

      RegressionSeedAuditReport audit =
          RegressionSeedAuditor.audit(projectDirectory, JazzerHarness.cliRequest());

      assertEquals(0, audit.totalSeedCount());
      assertEquals(0, audit.orphanedInputCount());
      assertEquals(1, audit.integrityProblemCount());
      assertTrue(
          audit.integrityProblems().stream()
              .anyMatch(problem -> "input-read-failure".equals(problem.problemKind())));
    } finally {
      Files.setPosixFilePermissions(unreadableInput, originalPermissions);
    }
  }

  private void writeCommittedSeed(
      JazzerHarness harness, String seedName, String inputJson, String coverageIntent)
      throws Exception {
    writeCommittedSeed(
        harness,
        seedName,
        inputJson,
        coverageIntent,
        JazzerReplayRunner.expectationFor(
            JazzerReplayRunner.replay(harness, inputJson.getBytes(UTF_8))));
  }

  private void writeCommittedSeed(
      JazzerHarness harness,
      String seedName,
      String inputJson,
      String coverageIntent,
      ReplayExpectation expectation)
      throws Exception {
    Path inputDirectory = harness.inputDirectory(projectDirectory);
    Path metadataDirectory = RegressionSeedPaths.metadataDirectory(projectDirectory, harness);
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);

    Path inputPath = inputDirectory.resolve(seedName + ".json");
    Files.writeString(inputPath, inputJson, UTF_8);
    RegressionSeedMetadata metadata =
        new RegressionSeedMetadata(
            harness.key(),
            projectDirectory.relativize(inputPath.toAbsolutePath().normalize()).toString(),
            coverageIntent,
            expectation);
    JazzerJson.write(metadataDirectory.resolve(seedName + ".json"), metadata);
  }
}
