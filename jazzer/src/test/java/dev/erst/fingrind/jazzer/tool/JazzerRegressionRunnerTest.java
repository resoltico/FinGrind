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
          """
          {
            "effectiveDate": "2026-04-07",
            "lines": [
              {
                "accountCode": "1000",
                "side": "DEBIT",
                "currencyCode": "EUR",
                "amount": "10.00"
              },
              {
                "accountCode": "2000",
                "side": "CREDIT",
                "currencyCode": "EUR",
                "amount": "10.00"
              }
            ],
            "provenance": {
              "actorId": "actor-1",
              "actorType": "AGENT",
              "commandId": "command-1",
              "idempotencyKey": "idem-1",
              "causationId": "cause-1",
              "recordedAt": "2026-04-07T10:15:30Z",
              "sourceChannel": "CLI"
            }
          }
          """);

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
                  "[JAZZER-PULSE] regression-target phase=plan target=cli-request total-inputs=1"));
      assertTrue(
          output
              .toString()
              .contains(
                  "[JAZZER-PULSE] regression-input target=cli-request completed=1/1 name=basic_valid.json status=SUCCESS"));
      assertTrue(
          output
              .toString()
              .contains(
                  "[JAZZER-PULSE] regression-target phase=finish target=cli-request status=SUCCESS"));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_returnsFailureWhenCommittedSeedDriftsFromRecordedExpectation() throws Exception {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();
      writeSeedMetadata(
          JazzerHarness.cliRequest(),
          "invalid_missing_provenance.json",
          """
          {
            "effectiveDate": "2026-04-07",
            "lines": [
              {
                "accountCode": "1000",
                "side": "DEBIT",
                "currencyCode": "EUR",
                "amount": "10.00"
              },
              {
                "accountCode": "2000",
                "side": "CREDIT",
                "currencyCode": "EUR",
                "amount": "10.00"
              }
            ]
          }
          """);

      Path metadataPath =
          RegressionSeedCatalog.metadataDirectory(projectDirectory, JazzerHarness.cliRequest())
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
          RegressionSeedCatalog.metadataDirectory(projectDirectory, JazzerHarness.cliRequest())
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
          RegressionSeedCatalog.metadataDirectory(projectDirectory, JazzerHarness.cliRequest())
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
      assertTrue(output.toString(UTF_8).contains("phase=finish target=cli-request status=SUCCESS"));
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
          RegressionSeedCatalog.metadataDirectory(projectDirectory, harness).resolve(fileName);
      Files.createDirectories(metadataPath.getParent());
      JazzerJson.write(metadataPath, metadata);
    }
  }

  @Test
  void main_replaysCommittedCliSeedsWithoutExiting() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    try (var ignored = new RedirectedSystemStreams(output, errors)) {
      assertDoesNotThrow(
          () ->
              JazzerRegressionRunner.main(
                  new String[] {
                    "--project-root", PROJECT_DIRECTORY.toString(), "--target", "cli-request"
                  }));
    }

    assertTrue(output.toString(UTF_8).contains("phase=finish target=cli-request status=SUCCESS"));
    assertNoRegressionErrors(errors.toString(UTF_8));
  }

  private static void assertNoRegressionErrors(String errorText) {
    assertFalse(errorText.contains("Regression mismatch"));
    assertFalse(errorText.contains("No regression metadata entries were found"));
    assertFalse(errorText.contains("Committed regression input does not exist"));
    assertFalse(errorText.contains("Regression metadata target mismatch"));
  }

  private static final class RedirectedSystemStreams implements AutoCloseable {
    private final java.io.PrintStream previousOut;
    private final java.io.PrintStream previousErr;
    private final java.io.PrintStream redirectedOut;
    private final java.io.PrintStream redirectedErr;

    private RedirectedSystemStreams(
        ByteArrayOutputStream redirectedOutput, ByteArrayOutputStream redirectedErrors) {
      previousOut = System.out;
      previousErr = System.err;
      redirectedOut = new java.io.PrintStream(redirectedOutput, false, UTF_8);
      redirectedErr = new java.io.PrintStream(redirectedErrors, false, UTF_8);
      System.setOut(redirectedOut);
      System.setErr(redirectedErr);
    }

    @Override
    public void close() {
      System.setOut(previousOut);
      System.setErr(previousErr);
      redirectedOut.close();
      redirectedErr.close();
    }
  }
}
