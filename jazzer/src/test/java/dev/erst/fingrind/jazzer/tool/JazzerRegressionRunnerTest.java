package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.jazzer.support.JazzerTestProjectRoot;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers direct committed-seed regression replay for one FinGrind harness. */
class JazzerRegressionRunnerTest {
  private static final Path PROJECT_DIRECTORY = JazzerTestProjectRoot.projectDirectory();

  @Nested
  class ParseHarness {
    @Test
    void parseHarness_returnsHarnessWhenArgumentsAreValid() {
      assertEquals(
          JazzerHarness.cliRequest(),
          JazzerRegressionRunner.parseHarness(new String[] {"--target", "cli-request"}));
    }

    @Test
    void parseHarness_throwsWhenArgumentsAreMissing() {
      assertThrows(
          IllegalArgumentException.class, () -> JazzerRegressionRunner.parseHarness(new String[0]));
    }

    @Test
    void parseHarness_throwsWhenFlagIsInvalid() {
      assertThrows(
          IllegalArgumentException.class,
          () -> JazzerRegressionRunner.parseHarness(new String[] {"--wrong", "cli-request"}));
    }

    @Test
    void parseHarness_throwsWhenTargetIsBlank() {
      assertThrows(
          IllegalArgumentException.class,
          () -> JazzerRegressionRunner.parseHarness(new String[] {"--target", " "}));
    }

    @Test
    void mainArguments_parse_rejects_invalid_shapes_and_normalizes_project_root() throws Exception {
      JazzerRegressionRunner.MainArguments parsedArguments =
          JazzerRegressionRunner.MainArguments.parse(
              new String[] {
                "--project-root", PROJECT_DIRECTORY.toString(), "--target", "cli-request"
              });
      assertEquals(
          PROJECT_DIRECTORY.toAbsolutePath().normalize(), parsedArguments.projectDirectory());
      assertEquals(
          java.util.List.of("--target", "cli-request"), parsedArguments.commandArguments());

      IllegalArgumentException usageError =
          assertThrows(
              IllegalArgumentException.class,
              () -> JazzerRegressionRunner.MainArguments.parse(new String[] {"--target"}));
      assertTrue(String.valueOf(usageError.getMessage()).contains("Usage: JazzerRegressionRunner"));

      IllegalArgumentException wrongFlagError =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  JazzerRegressionRunner.MainArguments.parse(
                      new String[] {
                        "--bogus", PROJECT_DIRECTORY.toString(), "--target", "cli-request"
                      }));
      assertTrue(
          String.valueOf(wrongFlagError.getMessage()).contains("Usage: JazzerRegressionRunner"));

      IllegalArgumentException blankRootError =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  JazzerRegressionRunner.MainArguments.parse(
                      new String[] {"--project-root", " ", "--target", "cli-request"}));
      assertTrue(
          String.valueOf(blankRootError.getMessage()).contains("--project-root must not be blank"));
    }
  }

  @Nested
  class Run {
    @TempDir Path projectDirectory;

    @Test
    void run_returnsSuccessWhenCommittedSeedMatchesRecordedExpectation() throws Exception {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();
      writeSeedMetadata(
          JazzerHarness.cliRequest(),
          "basic_valid.json",
          JazzerReplayRequestFixtures.basicValidRequest());

      int exitCode =
          JazzerRegressionRunner.run(
              projectDirectory,
              JazzerHarness.cliRequest(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(
          output
              .toString()
              .contains(
                  "[JAZZER-PULSE] regression-target event=plan target=cli-request total-inputs=1"));
      assertTrue(
          output
              .toString()
              .contains(
                  "[JAZZER-PULSE] regression-input target=cli-request completed=1/1 name=basic_valid.json status=SUCCESS"));
      assertTrue(
          output
              .toString()
              .contains(
                  "[JAZZER-PULSE] regression-target event=finish target=cli-request status=SUCCESS"));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_returnsFailureWhenCommittedSeedDriftsFromRecordedExpectation() throws Exception {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();
      writeSeedMetadata(
          JazzerHarness.cliRequest(),
          "invalid_missing_provenance.json",
          JazzerReplayRequestFixtures.invalidMissingProvenanceRequest());

      Path metadataPath =
          RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest())
              .resolve("invalid_missing_provenance.json");
      RegressionSeedMetadata metadata = JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
      JazzerJson.write(
          metadataPath,
          new RegressionSeedMetadata(
              metadata.targetKey(),
              metadata.inputPath(),
              metadata.coverageIntent(),
              new ReplayExpectation(
                  ReplayOutcomeKind.SUCCESS,
                  ReplayOutcome.SUCCESS_MESSAGE,
                  metadata.expectation().details())));

      int exitCode =
          JazzerRegressionRunner.run(
              projectDirectory,
              JazzerHarness.cliRequest(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(errors.toString().contains("Regression mismatch for cli-request input"));
    }

    @Test
    void run_returnsFailureWhenNoMetadataExists() throws Exception {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerRegressionRunner.run(
              projectDirectory,
              JazzerHarness.cliRequest(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(output.toString().isBlank());
      assertTrue(errors.toString().contains("No regression metadata entries were found"));
    }

    @Test
    void run_returnsFailureWhenMetadataTargetDoesNotMatchHarness() throws Exception {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();
      writeSeedMetadata(
          JazzerHarness.cliRequest(),
          "basic_valid.json",
          JazzerReplayRequestFixtures.basicValidRequest());
      Path metadataPath =
          RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest())
              .resolve("basic_valid.json");
      RegressionSeedMetadata metadata = JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
      JazzerJson.write(
          metadataPath,
          new RegressionSeedMetadata(
              "posting-workflow",
              metadata.inputPath(),
              metadata.coverageIntent(),
              metadata.expectation()));

      int exitCode =
          JazzerRegressionRunner.run(
              projectDirectory,
              JazzerHarness.cliRequest(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(errors.toString().contains("Regression metadata target mismatch"));
    }

    @Test
    void run_returnsFailureWhenCommittedInputIsMissing() throws Exception {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();
      writeSeedMetadata(
          JazzerHarness.cliRequest(),
          "basic_valid.json",
          JazzerReplayRequestFixtures.basicValidRequest());
      Path inputPath =
          JazzerHarness.cliRequest().inputDirectory(projectDirectory).resolve("basic_valid.json");
      Files.delete(inputPath);

      int exitCode =
          JazzerRegressionRunner.run(
              projectDirectory,
              JazzerHarness.cliRequest(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(errors.toString().contains("Committed regression input does not exist"));
    }

    @Test
    void run_printsStackTraceWhenUnexpectedReplayOutcomeDrifts() throws Exception {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();
      writeSeedMetadata(
          JazzerHarness.cliRequest(),
          "basic_valid.json",
          JazzerReplayRequestFixtures.basicValidRequest());

      int exitCode =
          JazzerRegressionRunner.run(
              projectDirectory,
              JazzerHarness.cliRequest(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true),
              (harness, input) ->
                  new ReplayOutcome.UnexpectedFailure(
                      harness.key(),
                      "IllegalStateException",
                      "boom",
                      "synthetic stack trace",
                      new UnparsedCliRequestReplayDetails()));

      assertEquals(1, exitCode);
      assertTrue(errors.toString().contains("Regression mismatch for cli-request input"));
      assertTrue(errors.toString().contains("synthetic stack trace"));
    }

    @Test
    void instanceRun_callsExitHandlerOnMismatch_and_main_rejects_invalid_arguments()
        throws Exception {
      writeSeedMetadata(
          JazzerHarness.cliRequest(),
          "basic_valid.json",
          JazzerReplayRequestFixtures.basicValidRequest());
      Path metadataPath =
          RegressionSeedPaths.metadataDirectory(projectDirectory, JazzerHarness.cliRequest())
              .resolve("basic_valid.json");
      RegressionSeedMetadata metadata = JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
      JazzerJson.write(
          metadataPath,
          new RegressionSeedMetadata(
              metadata.targetKey(),
              metadata.inputPath(),
              metadata.coverageIntent(),
              new ReplayExpectation(
                  ReplayOutcomeKind.UNEXPECTED_FAILURE, "boom", metadata.expectation().details())));

      AtomicInteger exitCode = new AtomicInteger(-1);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ByteArrayOutputStream errors = new ByteArrayOutputStream();

      new JazzerRegressionRunner(projectDirectory, output, errors, exitCode::set)
          .run(new String[] {"--target", "cli-request"});

      assertEquals(1, exitCode.get());
      assertTrue(errors.toString(UTF_8).contains("Regression mismatch for cli-request input"));

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  JazzerRegressionRunner.main(
                      new String[] {
                        "--project-root", PROJECT_DIRECTORY.toString(), "--target", " "
                      }));
      assertTrue(String.valueOf(exception.getMessage()).contains("targetKey must not be blank"));
    }

    @Test
    void instanceRun_skipsExitHandlerWhenRegressionReplaySucceeds() throws Exception {
      writeSeedMetadata(
          JazzerHarness.cliRequest(),
          "basic_valid.json",
          JazzerReplayRequestFixtures.basicValidRequest());

      AtomicInteger exitCode = new AtomicInteger(-1);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ByteArrayOutputStream errors = new ByteArrayOutputStream();

      new JazzerRegressionRunner(projectDirectory, output, errors, exitCode::set)
          .run(new String[] {"--target", "cli-request"});

      assertEquals(-1, exitCode.get());
      assertTrue(output.toString(UTF_8).contains("event=finish target=cli-request status=SUCCESS"));
      assertTrue(errors.toString(UTF_8).isBlank());
    }

    private void writeSeedMetadata(JazzerHarness harness, String fileName, String inputJson)
        throws Exception {
      Path inputPath = harness.inputDirectory(projectDirectory).resolve(fileName);
      Files.createDirectories(inputPath.getParent());
      Files.writeString(inputPath, inputJson.stripIndent(), UTF_8);

      RegressionSeedMetadata metadata =
          new RegressionSeedMetadata(
              harness.key(),
              projectDirectory.relativize(inputPath).toString(),
              "test replay seed",
              JazzerReplayRunner.expectationFor(
                  JazzerReplayRunner.replay(harness, Files.readAllBytes(inputPath))));
      Path metadataPath =
          RegressionSeedPaths.metadataDirectory(projectDirectory, harness).resolve(fileName);
      Files.createDirectories(metadataPath.getParent());
      JazzerJson.write(metadataPath, metadata);
    }
  }

  @Test
  void runMain_replaysCommittedCliSeedsWithoutInvokingExitHandler() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    AtomicInteger exitCode = new AtomicInteger(-1);

    assertDoesNotThrow(
        () ->
            JazzerRegressionRunner.runMain(
                new String[] {
                  "--project-root", PROJECT_DIRECTORY.toString(), "--target", "cli-request"
                },
                output,
                errors,
                exitCode::set));

    assertTrue(output.toString(UTF_8).contains("event=finish target=cli-request status=SUCCESS"));
    assertNoRegressionErrors(errors.toString(UTF_8));
    assertEquals(-1, exitCode.get());
  }

  @Test
  void main_replaysCommittedCliSeedsToCompletionInChildJvm() throws Exception {
    ChildJvmSupport.ChildProcessResult result =
        ChildJvmSupport.runMainClass(
            JazzerRegressionRunner.class,
            java.util.List.of(
                "--project-root", PROJECT_DIRECTORY.toString(), "--target", "cli-request"));

    assertEquals(0, result.exitCode());
    assertTrue(result.output().contains("event=finish target=cli-request status=SUCCESS"));
    assertNoRegressionErrors(result.output());
  }

  private static void assertNoRegressionErrors(String errorText) {
    assertFalse(errorText.contains("Regression mismatch"));
    assertFalse(errorText.contains("No regression metadata entries were found"));
    assertFalse(errorText.contains("Committed regression input does not exist"));
    assertFalse(errorText.contains("Regression metadata target mismatch"));
  }
}
