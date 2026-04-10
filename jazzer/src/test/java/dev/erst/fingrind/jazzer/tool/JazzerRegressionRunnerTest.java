package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers direct committed-seed regression replay for one FinGrind harness. */
class JazzerRegressionRunnerTest {
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
      assertThrows(IllegalArgumentException.class, () -> JazzerRegressionRunner.parseHarness(new String[0]));
    }

    @Test
    void parseHarness_throwsWhenTargetIsBlank() {
      assertThrows(
          IllegalArgumentException.class,
          () -> JazzerRegressionRunner.parseHarness(new String[] {"--target", " "}));
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
          output.toString().contains(
              "[JAZZER-PULSE] regression-target phase=plan target=cli-request total-inputs=1"));
      assertTrue(
          output.toString().contains(
              "[JAZZER-PULSE] regression-input target=cli-request completed=1/1 name=basic_valid.json status=SUCCESS"));
      assertTrue(
          output.toString().contains(
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
          RegressionSeedSupport.metadataDirectory(projectDirectory, JazzerHarness.cliRequest())
              .resolve("invalid_missing_provenance.json");
      RegressionSeedMetadata metadata = JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
      JazzerJson.write(
          metadataPath,
          new RegressionSeedMetadata(
              metadata.targetKey(),
              metadata.inputPath(),
              new ReplayExpectation("SUCCESS", metadata.expectation().details())));

      int exitCode =
          JazzerRegressionRunner.run(
              projectDirectory,
              JazzerHarness.cliRequest(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(errors.toString().contains("Regression mismatch for cli-request input"));
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
              JazzerReplaySupport.expectationFor(
                  JazzerReplaySupport.replay(harness, Files.readAllBytes(inputPath))));
      Path metadataPath = RegressionSeedSupport.metadataDirectory(projectDirectory, harness).resolve(fileName);
      Files.createDirectories(metadataPath.getParent());
      JazzerJson.write(metadataPath, metadata);
    }
  }
}
