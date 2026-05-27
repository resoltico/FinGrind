package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerRunTarget;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
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
    assertTrue(errors.toString().contains("promote-seed"));
    assertTrue(errors.toString().contains("seed-audit"));
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
        .run(new String[] {"replay", "cli-request"});

    assertEquals(1, exitCode.get());
    assertTrue(errors.toString(UTF_8).contains("Missing required input path."));

    ByteArrayOutputStream mainOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream mainErrors = new ByteArrayOutputStream();
    try (var ignored = new RedirectedSystemStreams(mainOutput, mainErrors)) {
      JazzerCli.main(new String[] {"--help"});
    }

    assertTrue(mainOutput.toString(UTF_8).contains("Commands:"));
    assertTrue(mainOutput.toString(UTF_8).contains("promote-seed"));
    assertTrue(mainErrors.toString(UTF_8).isBlank());
  }

  @Test
  void instanceRun_writes_wrapper_exit_status_and_skips_exit_handler_when_managed()
      throws Exception {
    AtomicInteger exitCode = new AtomicInteger(-1);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    Path exitStatusFile = projectDirectory.resolve("wrapper-exit-status.txt");
    String previousProperty = System.getProperty("fingrind.jazzer.wrapper.exit-status-file");

    System.setProperty("fingrind.jazzer.wrapper.exit-status-file", exitStatusFile.toString());
    try {
      new JazzerCli(projectDirectory, output, errors, exitCode::set).run(new String[] {"replay"});
    } finally {
      if (previousProperty == null) {
        System.clearProperty("fingrind.jazzer.wrapper.exit-status-file");
      } else {
        System.setProperty("fingrind.jazzer.wrapper.exit-status-file", previousProperty);
      }
    }

    assertEquals(-1, exitCode.get());
    assertEquals("1", Files.readString(exitStatusFile, UTF_8).trim());
    assertTrue(errors.toString(UTF_8).contains("Missing required target key."));
  }

  @Test
  void instanceRun_treats_blank_wrapper_exit_status_property_as_unmanaged() throws Exception {
    AtomicInteger exitCode = new AtomicInteger(-1);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    String previousProperty = System.getProperty("fingrind.jazzer.wrapper.exit-status-file");

    System.setProperty("fingrind.jazzer.wrapper.exit-status-file", "   ");
    try {
      new JazzerCli(projectDirectory, output, errors, exitCode::set).run(new String[] {"replay"});
    } finally {
      if (previousProperty == null) {
        System.clearProperty("fingrind.jazzer.wrapper.exit-status-file");
      } else {
        System.setProperty("fingrind.jazzer.wrapper.exit-status-file", previousProperty);
      }
    }

    assertEquals(1, exitCode.get());
    assertTrue(errors.toString(UTF_8).contains("Missing required target key."));
  }

  @Test
  void run_printsCanonicalActiveTargetKeys_and_rejectsUnexpectedArguments() throws Exception {
    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();

    int exitCode =
        JazzerCli.run(
            projectDirectory,
            new String[] {"active-target-keys"},
            new PrintWriter(output, true),
            new PrintWriter(errors, true));

    assertEquals(0, exitCode);
    assertEquals(
        java.util.Arrays.stream(JazzerRunTarget.values())
            .filter(JazzerRunTarget::activeFuzzing)
            .map(JazzerRunTarget::key)
            .toList(),
        output.toString().lines().filter(line -> !line.isBlank()).toList());
    assertTrue(errors.toString().isBlank());

    AtomicInteger rejectedExitCode = new AtomicInteger(-1);
    ByteArrayOutputStream rejectedOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream rejectedErrors = new ByteArrayOutputStream();
    new JazzerCli(projectDirectory, rejectedOutput, rejectedErrors, rejectedExitCode::set)
        .run(new String[] {"active-target-keys", "--bogus"});
    assertEquals(1, rejectedExitCode.get());
    assertTrue(
        rejectedErrors
            .toString(UTF_8)
            .contains("active-target-keys does not accept additional arguments."));
  }

  @Test
  void mainArguments_parse_rejects_invalid_shapes_and_normalizes_supported_entrypoints()
      throws Exception {
    JazzerCliMainArguments helpArguments = JazzerCliMainArguments.parse(new String[] {"--help"});
    assertEquals(
        "fingrind-unused-jazzer-root", helpArguments.projectDirectory().getFileName().toString());
    assertEquals(List.of("--help"), helpArguments.commandArguments());

    JazzerCliMainArguments emptyArguments = JazzerCliMainArguments.parse(new String[0]);
    assertTrue(emptyArguments.commandArguments().isEmpty());

    JazzerCliMainArguments activeTargetArguments =
        JazzerCliMainArguments.parse(new String[] {"active-target-keys"});
    assertEquals(List.of("active-target-keys"), activeTargetArguments.commandArguments());

    JazzerCliMainArguments replayArguments =
        JazzerCliMainArguments.parse(
            new String[] {
              "--project-root", projectDirectory.toString(), "replay", "cli-request", "input.bin"
            });
    assertEquals(projectDirectory.toAbsolutePath().normalize(), replayArguments.projectDirectory());
    assertEquals(List.of("replay", "cli-request", "input.bin"), replayArguments.commandArguments());

    IllegalArgumentException usageError =
        assertThrows(
            IllegalArgumentException.class,
            () -> JazzerCliMainArguments.parse(new String[] {"replay"}));
    assertTrue(String.valueOf(usageError.getMessage()).contains("Usage: JazzerCli"));

    IllegalArgumentException wrongFlagError =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JazzerCliMainArguments.parse(
                    new String[] {"--bogus", projectDirectory.toString(), "replay"}));
    assertTrue(String.valueOf(wrongFlagError.getMessage()).contains("Usage: JazzerCli"));

    IllegalArgumentException blankRootError =
        assertThrows(
            IllegalArgumentException.class,
            () -> JazzerCliMainArguments.parse(new String[] {"--project-root", " ", "replay"}));
    assertTrue(
        String.valueOf(blankRootError.getMessage()).contains("--project-root must not be blank"));
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
              new String[] {"replay", "cli-request", inputPath.toString()},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(output.toString().contains("Outcome: expected-invalid"));
      assertTrue(output.toString().contains("Message: Failed to read request JSON at line"));
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
              new String[] {"replay", "cli-request", inputPath.toString(), "--json"},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(output.toString().contains("\"outcomeKind\" : \"expected-invalid\""));
      assertTrue(output.toString().contains("\"invalidKind\" : \"CliRequestException\""));
      assertTrue(output.toString().contains("\"message\" : \"Failed to read request JSON at line"));
      assertTrue(output.toString().contains("\"type\" : \"CLI_REQUEST_UNPARSED\""));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_rejects_invalid_replay_arguments() throws Exception {
      Path inputPath = projectDirectory.resolve("raw-input.bin");
      Files.writeString(inputPath, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);

      StringWriter unexpectedExtraArgumentErrors = new StringWriter();
      int unexpectedExtraArgumentExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "cli-request", inputPath.toString(), "extra"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(unexpectedExtraArgumentErrors, true));
      assertEquals(1, unexpectedExtraArgumentExitCode);
      assertTrue(
          unexpectedExtraArgumentErrors.toString().contains("Unexpected replay argument: extra"));

      StringWriter aggregateErrors = new StringWriter();
      int aggregateExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "regression", inputPath.toString()},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(aggregateErrors, true));
      assertEquals(1, aggregateExitCode);
      assertTrue(aggregateErrors.toString().contains("Replay requires a single-harness target"));

      StringWriter missingInputErrors = new StringWriter();
      int missingInputExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "cli-request"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(missingInputErrors, true));
      assertEquals(1, missingInputExitCode);
      assertTrue(missingInputErrors.toString().contains("Missing required input path."));

      Path missingFilePath = projectDirectory.resolve("missing-input.bin");
      StringWriter missingFileErrors = new StringWriter();
      int missingFileExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "cli-request", missingFilePath.toString()},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(missingFileErrors, true));
      assertEquals(1, missingFileExitCode);
      assertTrue(
          missingFileErrors
              .toString()
              .contains(
                  "Replay input path does not exist: "
                      + missingFilePath.toAbsolutePath().normalize()));

      Path inputDirectoryPath = Files.createDirectory(projectDirectory.resolve("input-directory"));
      StringWriter directoryInputErrors = new StringWriter();
      int directoryInputExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "cli-request", inputDirectoryPath.toString()},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(directoryInputErrors, true));
      assertEquals(1, directoryInputExitCode);
      assertTrue(
          directoryInputErrors
              .toString()
              .contains(
                  "Replay input path must be a regular file: "
                      + inputDirectoryPath.toAbsolutePath().normalize()));

      StringWriter missingTargetErrors = new StringWriter();
      int missingTargetExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(missingTargetErrors, true));
      assertEquals(1, missingTargetExitCode);
      assertTrue(missingTargetErrors.toString().contains("Missing required target key."));

      StringWriter flagOnlyErrors = new StringWriter();
      StringWriter flagOnlyOutput = new StringWriter();
      int flagOnlyExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "--json"},
              new PrintWriter(flagOnlyOutput, true),
              new PrintWriter(flagOnlyErrors, true));
      assertEquals(1, flagOnlyExitCode);
      assertTrue(flagOnlyOutput.toString().contains("\"status\" : \"error\""));
      assertTrue(flagOnlyOutput.toString().contains("Missing required target key."));
      assertTrue(flagOnlyErrors.toString().isBlank());

      StringWriter jsonBeforeInputErrors = new StringWriter();
      StringWriter jsonBeforeInputOutput = new StringWriter();
      int jsonBeforeInputExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "cli-request", "--json"},
              new PrintWriter(jsonBeforeInputOutput, true),
              new PrintWriter(jsonBeforeInputErrors, true));
      assertEquals(1, jsonBeforeInputExitCode);
      assertTrue(jsonBeforeInputOutput.toString().contains("\"status\" : \"error\""));
      assertTrue(jsonBeforeInputOutput.toString().contains("Missing required input path."));
      assertTrue(jsonBeforeInputErrors.toString().isBlank());

      StringWriter unexpectedArgumentErrors = new StringWriter();
      int unexpectedArgumentExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "cli-request", inputPath.toString(), "--bogus"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(unexpectedArgumentErrors, true));
      assertEquals(1, unexpectedArgumentExitCode);
      assertTrue(
          unexpectedArgumentErrors.toString().contains("Unexpected replay argument: --bogus"));

      StringWriter unexpectedAfterJsonErrors = new StringWriter();
      StringWriter unexpectedAfterJsonOutput = new StringWriter();
      int unexpectedAfterJsonExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"replay", "cli-request", inputPath.toString(), "--json", "extra"},
              new PrintWriter(unexpectedAfterJsonOutput, true),
              new PrintWriter(unexpectedAfterJsonErrors, true));
      assertEquals(1, unexpectedAfterJsonExitCode);
      assertTrue(unexpectedAfterJsonOutput.toString().contains("\"status\" : \"error\""));
      assertTrue(
          unexpectedAfterJsonOutput.toString().contains("Unexpected replay argument: extra"));
      assertTrue(unexpectedAfterJsonErrors.toString().isBlank());
    }

    @Test
    void run_reports_replay_input_read_failures_as_usage_errors() throws Exception {
      Path inputPath = projectDirectory.resolve("unreadable-input.bin");
      Files.writeString(inputPath, JazzerReplayRequestFixtures.basicValidRequest(), UTF_8);
      Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(inputPath);
      try {
        Files.setPosixFilePermissions(inputPath, Set.of());
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();

        int exitCode =
            JazzerCli.run(
                projectDirectory,
                new String[] {"replay", "cli-request", inputPath.toString()},
                new PrintWriter(output, true),
                new PrintWriter(errors, true));

        assertEquals(1, exitCode);
        assertTrue(output.toString().isBlank());
        assertTrue(
            errors
                .toString()
                .contains(
                    "Failed to read replay input path: " + inputPath.toAbsolutePath().normalize()));
      } finally {
        Files.setPosixFilePermissions(inputPath, originalPermissions);
      }
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
              new String[] {"list-findings", "cli-request"},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(
          output.toString().contains("Summary: actionable=0 expected-invalid=1 replay-clean=0"));
      assertTrue(output.toString().contains(rawArtifact.getFileName().toString()));
      assertTrue(output.toString().contains("expected-invalid"));
      assertTrue(output.toString().contains("Failed to read request JSON at line"));
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
              new String[] {"list-findings", "cli-request", "--json"},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(output.toString().contains("\"targetKey\" : \"cli-request\""));
      assertTrue(output.toString().contains("\"rawArtifactKind\" : \"crash\""));
      assertTrue(output.toString().contains("\"replayClassification\" : \"expected-invalid\""));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_listsAllTargetsInJsonModeWithoutAnExplicitTarget() throws Exception {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "--json"},
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(output.toString().startsWith("["));
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
              new String[] {"list-findings", "ledger-plan-request"},
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
              new String[] {"list-findings", "posting-workflow"},
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
              new String[] {"list-findings", "cli-request", "ledger-plan-request"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(
          errors.toString().contains("Unexpected list-findings argument: ledger-plan-request"));

      StringWriter flagStyleErrors = new StringWriter();
      int flagStyleExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "--target"},
              new PrintWriter(new StringWriter(), true),
              new PrintWriter(flagStyleErrors, true));
      assertEquals(1, flagStyleExitCode);
      assertTrue(
          flagStyleErrors.toString().contains("Unexpected list-findings argument: --target"));

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

      StringWriter unexpectedAfterJsonErrors = new StringWriter();
      StringWriter unexpectedAfterJsonOutput = new StringWriter();
      int unexpectedAfterJsonExitCode =
          JazzerCli.run(
              projectDirectory,
              new String[] {"list-findings", "cli-request", "--json", "extra"},
              new PrintWriter(unexpectedAfterJsonOutput, true),
              new PrintWriter(unexpectedAfterJsonErrors, true));
      assertEquals(1, unexpectedAfterJsonExitCode);
      assertTrue(unexpectedAfterJsonOutput.toString().contains("\"status\" : \"error\""));
      assertTrue(
          unexpectedAfterJsonOutput
              .toString()
              .contains("Unexpected list-findings argument: extra"));
      assertTrue(unexpectedAfterJsonErrors.toString().isBlank());
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
        JazzerFindingListingTextRenderer.render(
            "cli-request", List.of(replayClean, expectedInvalid, unexpectedFailure));

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
