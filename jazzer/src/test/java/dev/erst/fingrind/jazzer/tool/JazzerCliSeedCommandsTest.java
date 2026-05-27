package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers the CLI seed-audit and promote-seed operator commands. */
class JazzerCliSeedCommandsTest {
  @TempDir Path projectDirectory;

  @Test
  void seedAudit_returnsFailureWhenCommittedDuplicateBytesExist() throws Exception {
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

    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();
    int exitCode =
        JazzerCli.run(
            projectDirectory,
            new String[] {"seed-audit"},
            new PrintWriter(output, true),
            new PrintWriter(errors, true));

    assertEquals(1, exitCode);
    assertTrue(output.toString().contains("Duplicate content groups: 1"));
    assertTrue(output.toString().contains("Duplicates:"));
    assertTrue(output.toString().contains("duplicate_cli.json"));
    assertTrue(errors.toString().isBlank());
  }

  @Test
  void promoteSeed_writesCommittedArtifactsAndSupportsJsonOutput() throws Exception {
    Path sourceInput = projectDirectory.resolve("raw-input.json");
    Files.writeString(
        sourceInput, JazzerReplayRequestFixtures.invalidDuplicateIdempotencyKeyRequest(), UTF_8);

    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();
    int exitCode =
        JazzerCli.run(
            projectDirectory,
            new String[] {
              "promote-seed",
              "cli-request",
              sourceInput.toString(),
              "--name",
              "duplicate_idempotency_key",
              "--intent",
              "duplicate object-key rejection",
              "--json"
            },
            new PrintWriter(output, true),
            new PrintWriter(errors, true));

    assertEquals(0, exitCode);
    assertTrue(output.toString().contains("\"targetKey\" : \"cli-request\""));
    assertTrue(
        output.toString().contains("\"coverageIntent\" : \"duplicate object-key rejection\""));
    assertTrue(errors.toString().isBlank());
  }

  @Test
  void promoteSeed_jsonMode_reports_deterministic_failures_as_machine_readable_payloads()
      throws Exception {
    Path invalidNameInput = projectDirectory.resolve("invalid-name-input.json");
    Files.writeString(invalidNameInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);

    CommandResult invalidNameResult =
        runCommand(
            "promote-seed",
            "cli-request",
            invalidNameInput.toString(),
            "--name",
            "Bad Seed",
            "--intent",
            "invalid name",
            "--json");
    assertEquals(1, invalidNameResult.exitCode());
    assertTrue(invalidNameResult.output().contains("\"status\" : \"error\""));
    assertTrue(invalidNameResult.output().contains("\"command\" : \"promote-seed\""));
    assertTrue(
        invalidNameResult.output().contains("seedName must use lower_snake_case ASCII letters"));
    assertTrue(invalidNameResult.output().contains("Try: bad_seed"));
    assertTrue(invalidNameResult.errors().isBlank());

    writeCommittedSeed(
        JazzerHarness.cliRequest(),
        "existing_seed",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "existing seed");
    Path duplicateSource = projectDirectory.resolve("duplicate-source.json");
    Files.writeString(duplicateSource, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);

    CommandResult duplicateResult =
        runCommand(
            "promote-seed",
            "cli-request",
            duplicateSource.toString(),
            "--name",
            "duplicate_seed",
            "--intent",
            "duplicate bytes",
            "--json");
    assertEquals(1, duplicateResult.exitCode());
    assertTrue(duplicateResult.output().contains("\"status\" : \"error\""));
    assertTrue(duplicateResult.output().contains("\"command\" : \"promote-seed\""));
    assertTrue(duplicateResult.output().contains("Committed seed content already exists at:"));
    assertTrue(duplicateResult.output().contains("existing_seed.json"));
    assertTrue(duplicateResult.errors().isBlank());
  }

  @Test
  void promoteSeed_plainTextMode_and_argument_validation_cover_operator_contract()
      throws Exception {
    Path sourceInput = projectDirectory.resolve("raw-input");
    Files.writeString(sourceInput, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
    Path directoryInput = projectDirectory.resolve("directory-input");
    Files.createDirectories(directoryInput);

    CommandResult plainPromotion =
        runCommand(
            "promote-seed",
            "cli-request",
            sourceInput.toString(),
            "--name",
            "plain_seed",
            "--intent",
            "plain promotion");
    assertEquals(0, plainPromotion.exitCode());
    assertTrue(plainPromotion.output().contains("Target: cli-request"));
    assertTrue(plainPromotion.output().contains("Committed input:"));
    assertTrue(plainPromotion.output().contains("Coverage intent: plain promotion"));
    assertTrue(plainPromotion.output().contains("Expectation:"));
    assertTrue(plainPromotion.errors().isBlank());

    assertUsageFailure("Missing required target key.", "promote-seed");
    assertUsageFailure("Missing required target key.", "promote-seed", "--json");
    assertUsageFailure("Missing required input path.", "promote-seed", "cli-request");
    assertUsageFailure("Missing required input path.", "promote-seed", "cli-request", "--name");
    assertUsageFailure(
        "Seed promotion input path does not exist:",
        "promote-seed",
        "cli-request",
        projectDirectory.resolve("missing.json").toString(),
        "--name",
        "missing_seed",
        "--intent",
        "missing input");
    assertUsageFailure(
        "Seed promotion input path must be a regular file:",
        "promote-seed",
        "cli-request",
        directoryInput.toString(),
        "--name",
        "directory_seed",
        "--intent",
        "directory input");
    assertUsageFailure(
        "Missing promote-seed seed name after --name.",
        "promote-seed",
        "cli-request",
        sourceInput.toString(),
        "--name",
        "--intent",
        "oops");
    assertUsageFailure(
        "Missing promote-seed seed name after --name.",
        "promote-seed",
        "cli-request",
        sourceInput.toString(),
        "--name");
    assertUsageFailure(
        "Missing promote-seed coverage intent after --intent.",
        "promote-seed",
        "cli-request",
        sourceInput.toString(),
        "--name",
        "needs_intent",
        "--intent");
    assertUsageFailure(
        "Missing promote-seed coverage intent after --intent.",
        "promote-seed",
        "cli-request",
        sourceInput.toString(),
        "--name",
        "needs_intent",
        "--intent",
        "--json");
    assertUsageFailure(
        "Duplicate promote-seed option: --name",
        "promote-seed",
        "cli-request",
        sourceInput.toString(),
        "--name",
        "first_seed",
        "--name",
        "second_seed",
        "--intent",
        "duplicate");
    assertUsageFailure(
        "Duplicate promote-seed option: --intent",
        "promote-seed",
        "cli-request",
        sourceInput.toString(),
        "--name",
        "duplicate_intent",
        "--intent",
        "first",
        "--intent",
        "second");
    assertUsageFailure(
        "Unexpected promote-seed argument: extra",
        "promote-seed",
        "cli-request",
        sourceInput.toString(),
        "--name",
        "unexpected_extra",
        "--intent",
        "unexpected",
        "extra");
    assertUsageFailure(
        "Missing required promote-seed option: --name.",
        "promote-seed",
        "cli-request",
        sourceInput.toString(),
        "--intent",
        "needs name");
    assertUsageFailure(
        "Missing required promote-seed option: --intent.",
        "promote-seed",
        "cli-request",
        sourceInput.toString(),
        "--name",
        "needs_intent");
    assertUsageFailure(
        "Unknown Jazzer run target:",
        "promote-seed",
        "not-a-target",
        sourceInput.toString(),
        "--name",
        "unknown_target",
        "--intent",
        "unknown target");
    assertUsageFailure(
        "Seed promotion requires a single-harness target, not regression",
        "promote-seed",
        "regression",
        sourceInput.toString(),
        "--name",
        "aggregate_seed",
        "--intent",
        "aggregate target");
  }

  @Test
  void seedAudit_returnsFailureWhenOrphansOrUnexpectedFailureSeedsExist() throws Exception {
    writeCommittedSeed(
        JazzerHarness.cliRequest(),
        "buggy_cli",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "buggy cli",
        new ReplayExpectation(
            ReplayOutcomeKind.UNEXPECTED_FAILURE, "boom", new UnparsedCliRequestReplayDetails()));

    Path inputDirectory = JazzerHarness.postingWorkflow().inputDirectory(projectDirectory);
    Files.createDirectories(inputDirectory);
    Files.writeString(
        inputDirectory.resolve("orphan.json"),
        JazzerReplayRequestFixtures.basicValidRequest(),
        UTF_8);

    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();
    int exitCode =
        JazzerCli.run(
            projectDirectory,
            new String[] {"seed-audit"},
            new PrintWriter(output, true),
            new PrintWriter(errors, true));

    assertEquals(1, exitCode);
    assertTrue(output.toString().contains("Orphaned input files: 1"));
    assertTrue(output.toString().contains("Unexpected-failure expectations: 1"));
    assertTrue(output.toString().contains("Integrity problems: 0"));
    assertTrue(output.toString().contains("Orphaned inputs:"));
    assertTrue(output.toString().contains("Unexpected-failure expectations:"));
    assertTrue(output.toString().contains("orphan.json"));
    assertTrue(output.toString().contains("buggy_cli.json"));
    assertTrue(errors.toString().isBlank());
  }

  @Test
  void seedAudit_returnsFailureFor_orphan_only_and_unexpected_failure_only_reports()
      throws Exception {
    Path orphanInputDirectory = JazzerHarness.cliRequest().inputDirectory(projectDirectory);
    Files.createDirectories(orphanInputDirectory);
    Files.writeString(orphanInputDirectory.resolve("orphan_only.json"), "{\"raw\":true}", UTF_8);

    CommandResult orphanOnlyAudit = runCommand("seed-audit");
    assertEquals(1, orphanOnlyAudit.exitCode());
    assertTrue(orphanOnlyAudit.output().contains("Orphaned input files: 1"));
    assertTrue(orphanOnlyAudit.output().contains("Unexpected-failure expectations: 0"));
    assertTrue(orphanOnlyAudit.output().contains("Integrity problems: 0"));

    Path unexpectedProjectDirectory = projectDirectory.resolve("unexpected-only");
    Files.createDirectories(unexpectedProjectDirectory);
    writeCommittedSeed(
        JazzerHarness.cliRequest(),
        "unexpected_only",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "unexpected only",
        new ReplayExpectation(
            ReplayOutcomeKind.UNEXPECTED_FAILURE, "boom", new UnparsedCliRequestReplayDetails()),
        unexpectedProjectDirectory);

    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();
    int exitCode =
        JazzerCli.run(
            unexpectedProjectDirectory,
            new String[] {"seed-audit"},
            new PrintWriter(output, true),
            new PrintWriter(errors, true));

    assertEquals(1, exitCode);
    assertTrue(output.toString().contains("Orphaned input files: 0"));
    assertTrue(output.toString().contains("Unexpected-failure expectations: 1"));
    assertTrue(output.toString().contains("Integrity problems: 0"));
    assertTrue(errors.toString().isBlank());
  }

  @Test
  void seedAudit_returnsFailureWhen_committed_seed_integrity_is_broken() throws Exception {
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(metadataDirectory);
    Files.createDirectories(projectDirectory.resolve("tmp"));

    Path escapedInput = projectDirectory.resolve("tmp").resolve("escaped.json");
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

    Path malformedInputDirectory = JazzerHarness.cliRequest().inputDirectory(projectDirectory);
    Files.createDirectories(malformedInputDirectory);
    Path malformedInput = malformedInputDirectory.resolve("malformed.json");
    Files.writeString(malformedInput, "{\"valid\":true}\n{\"extra\":false}\n", UTF_8);
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

    CommandResult plainAudit = runCommand("seed-audit");
    assertEquals(1, plainAudit.exitCode());
    assertTrue(plainAudit.output().contains("Integrity problems: 2"));
    assertTrue(plainAudit.output().contains("input-outside-harness"));
    assertTrue(plainAudit.output().contains("input-json-malformed"));
    assertTrue(plainAudit.errors().isBlank());

    CommandResult jsonAudit = runCommand("seed-audit", "--json");
    assertEquals(1, jsonAudit.exitCode());
    assertTrue(jsonAudit.output().contains("\"integrityProblemCount\" : 2"));
    assertTrue(jsonAudit.output().contains("\"problemKind\" : \"input-outside-harness\""));
    assertTrue(jsonAudit.output().contains("\"problemKind\" : \"input-json-malformed\""));
    assertTrue(jsonAudit.errors().isBlank());
  }

  @Test
  void seedAudit_returnsFailureWhen_committed_metadata_is_unreadable() throws Exception {
    Path metadataDirectory =
        RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest());
    Files.createDirectories(metadataDirectory);
    Files.writeString(metadataDirectory.resolve("broken.json"), "{broken", UTF_8);

    CommandResult plainAudit = runCommand("seed-audit");
    assertEquals(1, plainAudit.exitCode());
    assertTrue(plainAudit.output().contains("Integrity problems: 1"));
    assertTrue(plainAudit.output().contains("metadata-read-failure"));
    assertTrue(plainAudit.output().contains("broken.json"));
    assertTrue(plainAudit.errors().isBlank());

    CommandResult jsonAudit = runCommand("seed-audit", "--json");
    assertEquals(1, jsonAudit.exitCode());
    assertTrue(jsonAudit.output().contains("\"integrityProblemCount\" : 1"));
    assertTrue(jsonAudit.output().contains("\"problemKind\" : \"metadata-read-failure\""));
    assertTrue(jsonAudit.errors().isBlank());
  }

  @Test
  void seedAudit_supports_json_target_filters_and_argument_validation() throws Exception {
    writeCommittedSeed(
        JazzerHarness.cliRequest(),
        "valid_cli",
        JazzerReplayRequestFixtures.basicValidRequest(),
        "valid cli");

    CommandResult plainAudit = runCommand("seed-audit", "cli-request");
    assertEquals(0, plainAudit.exitCode());
    assertTrue(plainAudit.output().contains("Target: cli-request (1)"));
    assertTrue(plainAudit.output().contains("valid_cli.json | success | valid cli"));
    assertTrue(plainAudit.output().contains("Integrity problems: 0"));
    assertTrue(plainAudit.errors().isBlank());

    CommandResult globalJsonAudit = runCommand("seed-audit", "--json");
    assertEquals(0, globalJsonAudit.exitCode());
    assertTrue(globalJsonAudit.output().contains("\"totalSeedCount\" : 1"));
    assertTrue(globalJsonAudit.output().contains("\"integrityProblemCount\" : 0"));
    assertTrue(globalJsonAudit.output().contains("\"targetKey\" : \"cli-request\""));
    assertTrue(globalJsonAudit.errors().isBlank());

    CommandResult jsonAudit = runCommand("seed-audit", "cli-request", "--json");
    assertEquals(0, jsonAudit.exitCode());
    assertTrue(jsonAudit.output().contains("\"targetKey\" : \"cli-request\""));
    assertTrue(jsonAudit.output().contains("\"orphanedInputCount\" : 0"));
    assertTrue(jsonAudit.errors().isBlank());

    assertUsageFailure(
        "Seed audit requires a single-harness target, not regression", "seed-audit", "regression");
    assertUsageFailure(
        "Unexpected seed-audit argument: extra", "seed-audit", "cli-request", "extra");
    assertUsageFailure(
        "Unexpected seed-audit argument: extra", "seed-audit", "cli-request", "--json", "extra");
    assertUsageFailure("Unexpected seed-audit argument: --bogus", "seed-audit", "--bogus");
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
    writeCommittedSeed(harness, seedName, inputJson, coverageIntent, expectation, projectDirectory);
  }

  private void writeCommittedSeed(
      JazzerHarness harness,
      String seedName,
      String inputJson,
      String coverageIntent,
      ReplayExpectation expectation,
      Path rootDirectory)
      throws Exception {
    Path inputDirectory = harness.inputDirectory(rootDirectory);
    Path metadataDirectory = RegressionSeedPaths.metadataDirectory(rootDirectory, harness);
    Files.createDirectories(inputDirectory);
    Files.createDirectories(metadataDirectory);

    Path inputPath = inputDirectory.resolve(seedName + ".json");
    Files.writeString(inputPath, inputJson, UTF_8);
    RegressionSeedMetadata metadata =
        new RegressionSeedMetadata(
            harness.key(),
            rootDirectory.relativize(inputPath.toAbsolutePath().normalize()).toString(),
            coverageIntent,
            expectation);
    JazzerJson.write(metadataDirectory.resolve(seedName + ".json"), metadata);
  }

  private CommandResult runCommand(String... arguments) throws Exception {
    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();
    int exitCode =
        JazzerCli.run(
            projectDirectory,
            arguments,
            new PrintWriter(output, true),
            new PrintWriter(errors, true));
    return new CommandResult(exitCode, output.toString(), errors.toString());
  }

  private void assertUsageFailure(String expectedErrorFragment, String... arguments)
      throws Exception {
    CommandResult result = runCommand(arguments);
    assertEquals(1, result.exitCode());
    boolean jsonMode = java.util.Arrays.asList(arguments).contains("--json");
    if (jsonMode) {
      assertTrue(result.output().contains("\"status\" : \"error\""));
      assertTrue(result.output().contains(expectedErrorFragment));
      assertTrue(result.errors().isBlank());
      return;
    }
    assertTrue(result.output().isBlank());
    assertTrue(result.errors().contains(expectedErrorFragment));
  }

  private record CommandResult(int exitCode, String output, String errors) {}
}
