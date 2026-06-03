package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Discovery help and front-door command tests for {@link FinGrindCli}. */
class FinGrindCliDiscoveryHelpCommandTest extends FinGrindCliDiscoveryCommandTestSupport {
  @Test
  void run_rendersTextHelpWhenExplicitlyRequested() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", "--output", "text"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("FinGrind Help"));
    assertTrue(help.contains("open-book"));
    assertTrue(help.contains("declare-account"));
    assertTrue(help.contains("list-accounts"));
    assertTrue(help.contains("Command Families"));
    assertTrue(help.contains("First Successful Run"));
    assertTrue(help.contains("Generate one key file"));
    assertTrue(help.contains("Review the seeded starter chart"));
    assertTrue(help.contains("Print the first entry scaffold"));
    assertTrue(
        containsCollapsedText(
            help,
            CliInvocationText.commandExample(dev.erst.fingrind.contract.protocol.OperationId.HELP)
                + " <command>"));
    assertTrue(
        containsCollapsedText(
            help,
            CliInvocationText.commandExample(
                dev.erst.fingrind.contract.protocol.OperationId.PRINT_REQUEST_TEMPLATE)));
    assertFalse(help.contains("Guidance"));
    assertFalse(help.contains("declare-account-supplemental-cash-reserve.json"));
    assertFalse(help.contains("provenance.idempotencyKey"));
  }

  @Test
  void run_returnsScopedHelpForExplicitHelpTopic() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", "post-entry", "--output", "text"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("Try It"));
    assertTrue(help.contains("Before You Run"));
    assertTrue(help.contains("Request File"));
    assertTrue(help.contains("Command"));
    assertTrue(help.contains("post-entry"));
    assertTrue(help.contains("--request-file <path|->"));
    assertTrue(
        containsCollapsedText(
            help,
            CliInvocationText.commandExample(
                    dev.erst.fingrind.contract.protocol.OperationId.PRINT_REQUEST_TEMPLATE)
                + " post-entry > request.json"));
    assertTrue(help.contains("Starter file command"));
    assertFalse(help.contains("Output Contract"));
  }

  @Test
  void run_defaultsDiscoveryCommandsToJsonWhenStdoutIsRedirected() throws Exception {
    ByteArrayOutputStream helpOutput = new ByteArrayOutputStream();
    FinGrindCli helpCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(helpOutput), fixedClock());
    int helpExitCode = helpCli.run(new String[] {"help"});
    assertEquals(0, helpExitCode);
    JsonNode helpEnvelope =
        new ObjectMapper().readTree(helpOutput.toString(StandardCharsets.UTF_8));
    assertEquals("ok", helpEnvelope.path("status").stringValue());
    assertEquals("minimal", helpEnvelope.path("payload").path("detail").stringValue());
    assertTrue(helpEnvelope.path("payload").path("commands").isArray());

    ByteArrayOutputStream versionOutput = new ByteArrayOutputStream();
    FinGrindCli versionCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(versionOutput), fixedClock());
    int versionExitCode = versionCli.run(new String[] {"version"});
    assertEquals(0, versionExitCode);
    JsonNode versionEnvelope =
        new ObjectMapper().readTree(versionOutput.toString(StandardCharsets.UTF_8));
    assertEquals("ok", versionEnvelope.path("status").stringValue());
    assertTrue(versionEnvelope.path("payload").has("version"));

    ByteArrayOutputStream capabilitiesOutput = new ByteArrayOutputStream();
    FinGrindCli capabilitiesCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(capabilitiesOutput),
            fixedClock());
    int capabilitiesExitCode = capabilitiesCli.run(new String[] {"capabilities"});
    assertEquals(0, capabilitiesExitCode);
    JsonNode capabilitiesEnvelope =
        new ObjectMapper().readTree(capabilitiesOutput.toString(StandardCharsets.UTF_8));
    assertEquals("ok", capabilitiesEnvelope.path("status").stringValue());
    assertEquals("minimal", capabilitiesEnvelope.path("payload").path("detail").stringValue());
    assertEquals("overview", capabilitiesEnvelope.path("payload").path("focus").stringValue());
    assertTrue(capabilitiesEnvelope.path("payload").path("requestInput").isObject());
    assertTrue(capabilitiesEnvelope.path("payload").path("bookBoundary").isTextual());
    assertTrue(capabilitiesEnvelope.path("payload").path("builtInStatements").isArray());
    assertFalse(capabilitiesEnvelope.path("payload").has("storageEngines"));
    assertFalse(capabilitiesEnvelope.path("payload").has("commands"));
  }

  @Test
  void run_helpFullJsonPublishesExpandedOverviewContract() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"help", "--detail", "full", "--output", "json"});

    assertEquals(0, exitCode);
    JsonNode payload = new ObjectMapper().readTree(outputStream.toByteArray()).path("payload");
    assertEquals("full", payload.path("detail").stringValue());
    JsonNode fullContract = payload.path("fullContract");
    assertTrue(fullContract.isObject());
    assertTrue(fullContract.path("bookModel").isObject());
    assertTrue(fullContract.path("bookkeepingKernel").isObject());
    assertTrue(fullContract.path("currencyModel").isObject());
    assertTrue(fullContract.path("extensionSurface").isMissingNode());
    assertTrue(fullContract.path("quickStart").isArray());
  }

  @Test
  void run_rejectsHelpDetailOnTextOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"help", "--output", "text", "--detail", "full"});

    assertEquals(1, exitCode);
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("invalid-request"));
    assertTrue(output.contains("Argument : --output"));
    assertTrue(output.contains("resolved output mode is json"));
  }

  @Test
  void run_rejectsCapabilitiesDetailOnTextOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"capabilities", "--output", "text", "--detail", "full"});

    assertEquals(1, exitCode);
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("invalid-request"));
    assertTrue(output.contains("Argument : --output"));
    assertTrue(output.contains("resolved output mode is json"));
  }

  @Test
  void run_returnsScopedHelpForCommandHelpAlias() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"post-entry", "--help", "--output", "text"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("post-entry"));
    assertTrue(help.contains("Try It"));
    assertTrue(help.contains("Request File"));
  }

  @Test
  void run_returnsTemplateHelpWithTemplateFamilySpecificOperatorNote() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", "print-request-template", "--output", "text"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("declare-account"));
    assertTrue(help.contains("runnable sample document"));
    assertTrue(containsCollapsedText(help, "placeholder evidence and provenance values"));
  }

  @Test
  void run_rewritesBundleHelpUsageAndHintsToTheBundleLauncher() throws Exception {
    String priorDistribution =
        System.getProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, "__missing__");
    try {
      System.setProperty(
          FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION);
      String bundleLauncher =
          CliInvocationText.launcherCommandFor(
              FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION, System.getProperty("os.name", ""));
      ByteArrayOutputStream helpOutputStream = new ByteArrayOutputStream();
      FinGrindCli helpCli =
          cli(
              new ByteArrayInputStream(new byte[0]),
              utf8PrintStream(helpOutputStream),
              fixedClock());
      ByteArrayOutputStream failureOutputStream = new ByteArrayOutputStream();
      FinGrindCli failureCli =
          cli(
              new ByteArrayInputStream(new byte[0]),
              utf8PrintStream(failureOutputStream),
              fixedClock());
      int helpExitCode = helpCli.run(new String[] {"help", "post-entry", "--output", "json"});
      int failureExitCode = failureCli.run(new String[] {"post-entry", "--bogus"});
      assertEquals(0, helpExitCode);
      assertEquals(1, failureExitCode);
      JsonNode helpPayload =
          new ObjectMapper()
              .readTree(helpOutputStream.toString(StandardCharsets.UTF_8))
              .path("payload");
      assertTrue(containsText(helpPayload, bundleLauncher + " post-entry"));
      JsonNode failurePayload =
          assertDoesNotThrow(
              () ->
                  new ObjectMapper()
                      .readTree(failureOutputStream.toString(StandardCharsets.UTF_8)));
      assertEquals("error", failurePayload.path("status").stringValue());
      assertEquals("Unsupported argument: --bogus", failurePayload.path("message").stringValue());
      assertTrue(
          failurePayload
              .path("hint")
              .stringValue()
              .contains(
                  "Run '"
                      + bundleLauncher
                      + " help post-entry' to inspect the supported command syntax."));
    } finally {
      if ("__missing__".equals(priorDistribution)) {
        System.clearProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
      } else {
        System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, priorDistribution);
      }
    }
  }

  @Test
  void run_rewritesSourceCheckoutHelpToTheGeneratedLauncherSurface() {
    assertRuntimeSpecificHelpSurface(FinGrindCli.SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION);
  }

  @Test
  void run_rewritesDirectJavaHelpToTheDeveloperJarSurface() {
    assertRuntimeSpecificHelpSurface(FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION);
  }

  @Test
  void run_rewritesContainerHelpToTheDockerSurface() {
    assertRuntimeSpecificHelpSurface(FinGrindCli.CONTAINER_RUNTIME_DISTRIBUTION);
  }

  @Test
  void run_invalidInvocationHonorsExplicitJsonOutputSelection() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"post-entry", "--bogus", "--output", "json"});

    assertEquals(1, exitCode);
    JsonNode failurePayload =
        new ObjectMapper().readTree(outputStream.toString(StandardCharsets.UTF_8));
    assertEquals("error", failurePayload.path("status").stringValue());
    assertEquals("Unsupported argument: --bogus", failurePayload.path("message").stringValue());
  }

  @Test
  void run_doesNotTouchWorkflowForDiscoveryCommands() {
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(java.nio.file.Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(accountPage(java.util.List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    int exitCode = cli.run(new String[] {"capabilities", "--output", "json"});
    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\""));
    assertFalse(workflow.workflowInvoked());
  }
}
