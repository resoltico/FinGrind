package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers the supported local Jazzer replay and finding-inspection operator commands. */
class JazzerCliTest {
  @TempDir Path projectDirectory;

  @Test
  void run_returnsFailureWhenSubcommandIsMissing() throws Exception {
    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();

    int exitCode =
        JazzerCli.run(
            projectDirectory,
            new String[0],
            new PrintWriter(output, true),
            new PrintWriter(errors, true));

    assertEquals(1, exitCode);
    assertTrue(output.toString().isBlank());
    assertTrue(errors.toString().contains("A Jazzer subcommand is required."));
    assertTrue(errors.toString().contains("Commands:"));
    assertTrue(errors.toString().contains("replay"));
    assertTrue(errors.toString().contains("list-findings"));
  }

  @Test
  void run_returnsUsageWhenSubcommandIsUnknown() throws Exception {
    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();

    int exitCode =
        JazzerCli.run(
            projectDirectory,
            new String[] {"missing-command"},
            new PrintWriter(output, true),
            new PrintWriter(errors, true));

    assertEquals(1, exitCode);
    assertTrue(output.toString().isBlank());
    assertTrue(errors.toString().contains("Unknown Jazzer subcommand: missing-command"));
    assertTrue(errors.toString().contains("Usage:"));
  }

  @Test
  void run_printsUsageForHelp() throws Exception {
    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();

    int exitCode =
        JazzerCli.run(
            projectDirectory,
            new String[] {"--help"},
            new PrintWriter(output, true),
            new PrintWriter(errors, true));

    assertEquals(0, exitCode);
    assertTrue(output.toString().contains("Usage:"));
    assertTrue(output.toString().contains("Replayable targets:"));
    assertTrue(errors.toString().isBlank());
  }

  @Test
  void instanceRun_callsExitHandlerForUsageErrors_and_main_printsHelp() throws Exception {
    AtomicInteger exitCode = new AtomicInteger(-1);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();

    new JazzerCli(projectDirectory, output, errors, exitCode::set)
        .run(new String[] {"replay", "--target", "cli-request"});

    assertEquals(1, exitCode.get());
    assertTrue(errors.toString(UTF_8).contains("Missing required option --input"));

    SystemStreams previousStreams = new SystemStreams(System.out, System.err);
    ByteArrayOutputStream mainOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream mainErrors = new ByteArrayOutputStream();
    try (var redirectedOut = new java.io.PrintStream(mainOutput, false, UTF_8);
        var redirectedErr = new java.io.PrintStream(mainErrors, false, UTF_8)) {
      System.setOut(redirectedOut);
      System.setErr(redirectedErr);
      JazzerCli.main(new String[] {"--help"});
    } finally {
      System.setOut(previousStreams.out());
      System.setErr(previousStreams.err());
    }

    assertTrue(mainOutput.toString(UTF_8).contains("Commands:"));
    assertTrue(mainErrors.toString(UTF_8).isBlank());
  }

  @Nested
  class Replay {
    @Test
    void run_replaysMalformedCliRequestAsExpectedInvalidText() throws Exception {
      Path inputPath = projectDirectory.resolve("raw-input.bin");
      Files.writeString(inputPath, "{sideevr:0}dee", UTF_8);
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "--target", "cli-request", "--input", inputPath.toString()},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(output.toString().contains("Outcome: expected-invalid"));
      assertTrue(output.toString().contains("Message: Failed to read request JSON."));
      assertTrue(output.toString().contains("\"type\" : \"CLI_REQUEST_UNPARSED\""));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_replaysMalformedCliRequestAsStructuredJsonWhenRequested() throws Exception {
      Path inputPath = projectDirectory.resolve("raw-input.bin");
      Files.writeString(inputPath, "{sideevr:0}dee", UTF_8);
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {
                "replay", "--target", "cli-request", "--input", inputPath.toString(), "--json"
              },
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(output.toString().contains("\"outcomeKind\" : \"expected-invalid\""));
      assertTrue(output.toString().contains("\"invalidKind\" : \"CliRequestException\""));
      assertTrue(output.toString().contains("\"message\" : \"Failed to read request JSON.\""));
      assertTrue(output.toString().contains("\"type\" : \"CLI_REQUEST_UNPARSED\""));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_rejects_duplicate_or_invalid_replay_arguments() throws Exception {
      Path inputPath = projectDirectory.resolve("raw-input.bin");
      Files.writeString(inputPath, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);

      StringWriter duplicateErrors = new StringWriter();
      int duplicateExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {
                "replay",
                "--target",
                "cli-request",
                "--target",
                "cli-request",
                "--input",
                inputPath.toString()
              },
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(duplicateErrors, true));
      assertEquals(1, duplicateExitCode);
      assertTrue(duplicateErrors.toString().contains("Duplicate option --target"));

      StringWriter aggregateErrors = new StringWriter();
      int aggregateExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "--target", "regression", "--input", inputPath.toString()},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(aggregateErrors, true));
      assertEquals(1, aggregateExitCode);
      assertTrue(aggregateErrors.toString().contains("Replay requires a single-harness target"));

      StringWriter missingValueErrors = new StringWriter();
      int missingValueExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "--target", "cli-request", "--input"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(missingValueErrors, true));
      assertEquals(1, missingValueExitCode);
      assertTrue(missingValueErrors.toString().contains("Missing value after --input"));

      StringWriter missingTargetErrors = new StringWriter();
      int missingTargetExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "--input", inputPath.toString()},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(missingTargetErrors, true));
      assertEquals(1, missingTargetExitCode);
      assertTrue(missingTargetErrors.toString().contains("Missing required option --target"));

      StringWriter unexpectedArgumentErrors = new StringWriter();
      int unexpectedArgumentExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {
                "replay", "--target", "cli-request", "--input", inputPath.toString(), "--bogus"
              },
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(unexpectedArgumentErrors, true));
      assertEquals(1, unexpectedArgumentExitCode);
      assertTrue(
          unexpectedArgumentErrors.toString().contains("Unexpected replay argument: --bogus"));
    }
  }

  @Nested
  class ListFindings {
    @Test
    void run_classifiesRawCrashArtifactsViaReplayInTextMode() throws Exception {
      Path runDirectory = projectDirectory.resolve(".local/runs/cli-request");
      Path rawArtifact = runDirectory.resolve("crash-f2dc46a6bf774c341d51ea2f2f47b11fd0a0c0db");
      Files.createDirectories(runDirectory);
      Files.writeString(rawArtifact, "{sideevr:0}dee", UTF_8);
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "--target", "cli-request"},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(
          output.toString().contains("Summary: actionable=0 expected-invalid=1 replay-clean=0"));
      assertTrue(output.toString().contains(rawArtifact.getFileName().toString()));
      assertTrue(output.toString().contains("expected-invalid"));
      assertTrue(output.toString().contains("Failed to read request JSON."));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_classifiesRawCrashArtifactsViaReplayInJsonMode() throws Exception {
      Path runDirectory = projectDirectory.resolve(".local/runs/cli-request");
      Path rawArtifact = runDirectory.resolve("crash-f2dc46a6bf774c341d51ea2f2f47b11fd0a0c0db");
      Files.createDirectories(runDirectory);
      Files.writeString(rawArtifact, "{sideevr:0}dee", UTF_8);
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "--target", "cli-request", "--json"},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(output.toString().contains("\"targetKey\" : \"cli-request\""));
      assertTrue(output.toString().contains("\"rawArtifactKind\" : \"crash\""));
      assertTrue(output.toString().contains("\"replayClassification\" : \"expected-invalid\""));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_classifiesRejectedLedgerPlanCrashArtifactAsReplayClean() throws Exception {
      Path runDirectory = projectDirectory.resolve(".local/runs/ledger-plan-request");
      Path rawArtifact = runDirectory.resolve("crash-04681e1f1007384b5694349fa088d0ccdc890bed");
      Files.createDirectories(runDirectory);
      Files.writeString(
          rawArtifact,
          JazzerReplayLedgerPlanFixtures.rejectedMissingBookListPostingsLedgerPlan(),
          UTF_8);
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "--target", "ledger-plan-request"},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(
          output.toString().contains("Summary: actionable=0 expected-invalid=0 replay-clean=1"));
      assertTrue(output.toString().contains(rawArtifact.getFileName().toString()));
      assertTrue(output.toString().contains("replay-clean"));
      assertTrue(output.toString().contains("Replay completed without surfacing a bug."));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_lists_all_targets_and_handles_empty_directories() throws Exception {
      Path cliRunDirectory = projectDirectory.resolve(".local/runs/cli-request");
      Path ledgerRunDirectory = projectDirectory.resolve(".local/runs/ledger-plan-request");
      Files.createDirectories(cliRunDirectory);
      Files.createDirectories(ledgerRunDirectory);
      Files.writeString(cliRunDirectory.resolve("timeout-a"), "{sideevr:0}dee", UTF_8);
      Files.writeString(
          ledgerRunDirectory.resolve("crash-b"),
          JazzerReplayLedgerPlanFixtures.rejectedMissingBookListPostingsLedgerPlan(),
          UTF_8);
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings"},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(output.toString().contains("Target: cli-request"));
      assertTrue(output.toString().contains("Target: ledger-plan-request"));
      assertTrue(output.toString().contains("timeout-a | expected-invalid"));
      assertTrue(output.toString().contains("crash-b | replay-clean"));
      assertTrue(
          output.toString().contains(System.lineSeparator() + System.lineSeparator() + "Target:"));
      assertTrue(errors.toString().isBlank());

      StringWriter emptyOutput = new StringWriter();
      int emptyExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "--target", "posting-workflow"},
              new PrintWriter(emptyOutput, true),
              new PrintWriter(new StringWriter(), true));
      assertEquals(0, emptyExitCode);
      assertTrue(
          emptyOutput
              .toString()
              .contains("No raw libFuzzer artifacts are currently recorded for this target."));
    }

    @Test
    void run_rejects_invalid_listFindings_arguments() throws Exception {
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "--target", "cli-request", "--target", "cli-request"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(errors.toString().contains("Duplicate option --target"));

      StringWriter missingValueErrors = new StringWriter();
      int missingValueExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "--target"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(missingValueErrors, true));
      assertEquals(1, missingValueExitCode);
      assertTrue(missingValueErrors.toString().contains("Missing value after --target"));

      StringWriter unexpectedArgumentErrors = new StringWriter();
      int unexpectedArgumentExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "--bogus"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(unexpectedArgumentErrors, true));
      assertEquals(1, unexpectedArgumentExitCode);
      assertTrue(
          unexpectedArgumentErrors
              .toString()
              .contains("Unexpected list-findings argument: --bogus"));
    }
  }

  @Test
  void helpers_renderMixedFindingListings_and_computeReplayExitCodes() throws Exception {
    FindingArtifact replayClean =
        new FindingArtifact(
            "cli-request",
            "crash",
            "crash-a",
            "/tmp/crash-a",
            ReplayFindingClassification.REPLAY_CLEAN,
            ReplayOutcome.SUCCESS_MESSAGE);
    FindingArtifact expectedInvalid =
        new FindingArtifact(
            "cli-request",
            "timeout",
            "timeout-b",
            "/tmp/timeout-b",
            ReplayFindingClassification.EXPECTED_INVALID,
            "bad input");
    FindingArtifact unexpectedFailure =
        new FindingArtifact(
            "cli-request",
            "oom",
            "oom-c",
            "/tmp/oom-c",
            ReplayFindingClassification.UNEXPECTED_FAILURE,
            "boom");

    String listing =
        (String)
            invokePrivate(
                "renderFindingListing",
                new Class<?>[] {String.class, List.class},
                "cli-request",
                List.of(replayClean, expectedInvalid, unexpectedFailure));

    assertTrue(listing.contains("Summary: actionable=1 expected-invalid=1 replay-clean=1"));
    assertTrue(listing.contains("oom-c | unexpected-failure | boom"));
    assertEquals(
        1,
        JazzerCli.replayExitCode(
            new ReplayOutcome.UnexpectedFailure(
                "cli-request",
                "IllegalStateException",
                "boom",
                "stack",
                new UnparsedCliRequestReplayDetails())));
    assertEquals(
        0,
        JazzerCli.replayExitCode(
            new ReplayOutcome.ExpectedInvalid(
                "cli-request",
                "CliRequestException",
                "bad input",
                new UnparsedCliRequestReplayDetails())));
  }

  private record SystemStreams(java.io.PrintStream out, java.io.PrintStream err) {}

  private static Object invokePrivate(
      String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
    Method method = JazzerCli.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    try {
      return method.invoke(null, arguments);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }
}
