package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliDiscoveryCommandTest extends FinGrindCliTestSupport {
  @Test
  void run_rendersHumanHelpWhenExplicitlyRequested() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", "--output", "human"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("FinGrind Help"));
    assertTrue(help.contains("open-book"));
    assertTrue(help.contains("declare-account"));
    assertTrue(help.contains("list-accounts"));
    assertTrue(help.contains("Command Groups"));
    assertTrue(help.contains("Start Here"));
    assertTrue(
        containsCollapsedText(
            help, "Run '" + CliInvocationText.commandExample(OperationId.HELP) + " <command>'"));
    assertTrue(containsCollapsedText(help, "syntax, request guidance, and runnable examples."));
    assertTrue(
        containsCollapsedText(
            help,
            "Run '"
                + CliInvocationText.commandExample(OperationId.CAPABILITIES)
                + " --output json'"));
    assertFalse(help.contains("Guidance"));
    assertFalse(help.contains("declare-account-cash.json"));
    assertFalse(help.contains("provenance.idempotencyKey"));
  }

  @Test
  void run_returnsScopedHelpForExplicitHelpTopic() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", "post-entry", "--output", "human"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("Invocation"));
    assertTrue(help.contains("Examples"));
    assertTrue(help.contains("Output Contract"));
    assertTrue(help.contains("Request Document"));
    assertTrue(help.contains("post-entry"));
    assertTrue(help.contains("--request-file <path|->"));
    assertTrue(help.contains("Scaffold command"));
    assertTrue(help.contains("Contract lookup"));
  }

  @Test
  void run_defaultsDiscoveryCommandsToJsonWhenStdoutIsRedirected() throws IOException {
    ByteArrayOutputStream helpOutput = new ByteArrayOutputStream();
    FinGrindCli helpCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(helpOutput), fixedClock());
    int helpExitCode = helpCli.run(new String[] {"help"});
    assertEquals(0, helpExitCode);
    JsonNode helpEnvelope =
        new ObjectMapper().readTree(helpOutput.toString(StandardCharsets.UTF_8));
    assertEquals("ok", helpEnvelope.path("status").stringValue());
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
    assertTrue(capabilitiesEnvelope.path("payload").path("commands").isObject());
  }

  @Test
  void run_helpFullJsonPublishesExpandedOverviewContract() throws IOException {
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
    assertTrue(fullContract.path("accountingBaseline").isObject());
    assertTrue(fullContract.path("currencyModel").isObject());
    assertTrue(fullContract.path("extensionSurface").isObject());
    assertTrue(fullContract.path("quickStart").isArray());
  }

  @Test
  void run_rejectsHelpDetailOnHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"help", "--output", "human", "--detail", "full"});

    assertEquals(1, exitCode);
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("invalid-request"));
    assertTrue(output.contains("Argument : --detail"));
    assertTrue(output.contains("resolved output mode is json"));
  }

  @Test
  void run_rejectsCapabilitiesDetailOnHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"capabilities", "--output", "human", "--detail", "full"});

    assertEquals(1, exitCode);
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("invalid-request"));
    assertTrue(output.contains("Argument : --detail"));
    assertTrue(output.contains("resolved output mode is json"));
  }

  @Test
  void run_returnsScopedHelpForCommandHelpAlias() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"post-entry", "--help", "--output", "human"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("post-entry"));
    assertTrue(help.contains("Invocation"));
    assertTrue(help.contains("Examples"));
  }

  @Test
  void run_returnsTemplateHelpWithTemplateFamilySpecificOperatorNote() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", "print-request-template", "--output", "human"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("declare-account"));
    assertTrue(help.contains("runnable sample document"));
    assertTrue(containsCollapsedText(help, "sample evidence and provenance values"));
  }

  @Test
  void run_rewritesBundleHelpUsageAndHintsToTheBundleLauncher() {
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
  void run_returnsCapabilities() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"capabilities", "--detail", "full", "--output", "json"});
    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    JsonNode payload = new ObjectMapper().readTree(json).path("payload");
    assertTrue(json.contains("\"administration\""));
    assertTrue(json.contains("\"query\""));
    assertTrue(json.contains("\"administration-book-not-initialized\""));
    assertTrue(json.contains("\"query-book-not-initialized\""));
    assertTrue(json.contains("\"posting-book-not-initialized\""));
    assertTrue(json.contains("\"account-role-conflict\""));
    assertTrue(json.contains("\"posting-not-found\""));
    assertCapabilitiesCommandCatalog(payload);
    assertCapabilitiesRequestShapes(payload);
    assertCapabilitiesRequestInput(payload);
    assertCapabilitiesResponseModel(payload);
    assertFalse(payload.has("environment"));
  }

  @Test
  void run_returnsEnvironment() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"environment", "--output", "json"});

    assertEquals(0, exitCode);
    JsonNode payload =
        new ObjectMapper().readTree(outputStream.toString(StandardCharsets.UTF_8)).path("payload");
    assertEnvironmentRuntimeContract(payload);
  }

  private static List<String> commandNames(JsonNode commands) {
    List<String> names = new ArrayList<>();
    commands.forEach(command -> names.add(command.path("name").stringValue()));
    return List.copyOf(names);
  }

  private static boolean containsText(JsonNode node, String expected) {
    if (node.isString() && node.stringValue().contains(expected)) {
      return true;
    }
    if (node.isObject() || node.isArray()) {
      for (JsonNode child : node) {
        if (containsText(child, expected)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean containsCollapsedText(String text, String expected) {
    return collapseWhitespace(text).contains(collapseWhitespace(expected));
  }

  private static String collapseWhitespace(String text) {
    return text.replaceAll("\\s+", " ").trim();
  }

  private void assertRuntimeSpecificHelpSurface(String runtimeDistribution) {
    String priorDistribution =
        System.getProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, "__missing__");
    try {
      System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, runtimeDistribution);
      ByteArrayOutputStream helpOutputStream = new ByteArrayOutputStream();
      FinGrindCli helpCli =
          cli(
              new ByteArrayInputStream(new byte[0]),
              utf8PrintStream(helpOutputStream),
              fixedClock());
      ByteArrayOutputStream commandHelpOutputStream = new ByteArrayOutputStream();
      FinGrindCli commandHelpCli =
          cli(
              new ByteArrayInputStream(new byte[0]),
              utf8PrintStream(commandHelpOutputStream),
              fixedClock());
      ByteArrayOutputStream failureOutputStream = new ByteArrayOutputStream();
      FinGrindCli failureCli =
          cli(
              new ByteArrayInputStream(new byte[0]),
              utf8PrintStream(failureOutputStream),
              fixedClock());
      int helpExitCode = helpCli.run(new String[] {"help", "--output", "human"});
      int commandHelpExitCode =
          commandHelpCli.run(new String[] {"help", "open-book", "--output", "human"});
      int failureExitCode = failureCli.run(new String[] {"post-entry", "--bogus"});
      assertEquals(0, helpExitCode);
      assertEquals(0, commandHelpExitCode);
      assertEquals(1, failureExitCode);
      String help = helpOutputStream.toString(StandardCharsets.UTF_8);
      String commandHelp = commandHelpOutputStream.toString(StandardCharsets.UTF_8);
      String launcher =
          CliInvocationText.launcherCommandFor(
              runtimeDistribution, System.getProperty("os.name", ""));
      assertTrue(help.contains("Start Here"), help);
      assertTrue(containsCollapsedText(help, launcher + " help <command>"), help);
      assertFalse(help.contains("Source Checkout Launcher"), help);
      assertFalse(help.contains("Developer Raw JAR"), help);
      assertFalse(help.contains("Container Image"), help);
      assertTrue(containsCollapsedText(commandHelp, launcher + " open-book"), commandHelp);
      JsonNode failurePayload =
          assertDoesNotThrow(
              () ->
                  new ObjectMapper()
                      .readTree(failureOutputStream.toString(StandardCharsets.UTF_8)));
      assertEquals("error", failurePayload.path("status").stringValue());
      assertTrue(
          failurePayload
              .path("hint")
              .stringValue()
              .contains(
                  "Run '"
                      + launcher
                      + " help post-entry' to inspect the supported command syntax."),
          failurePayload.toPrettyString());
    } finally {
      if ("__missing__".equals(priorDistribution)) {
        System.clearProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
      } else {
        System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, priorDistribution);
      }
    }
  }

  private static JsonNode commandDescriptor(JsonNode commands, String operationId) {
    for (JsonNode command : commands) {
      if (operationId.equals(command.path("name").stringValue())) {
        return command;
      }
    }
    throw new AssertionError("Missing command descriptor: " + operationId);
  }

  @Test
  void run_invalidInvocationHonorsExplicitJsonOutputSelection() throws IOException {
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

  private static void assertCapabilitiesCommandCatalog(JsonNode payload) {
    assertEquals(
        List.of(
            "generate-book-key-file",
            "open-book",
            "rekey-book",
            "backup-book",
            "restore-book",
            "inspect-rekey-rollback",
            "delete-rekey-rollback",
            "restore-rekey-rollback",
            "declare-account",
            "close-period"),
        commandNames(payload.path("commands").path("administration")));
    assertEquals(
        List.of(
            "inspect-book",
            "list-accounts",
            "get-posting",
            "list-postings",
            "account-balance",
            "trial-balance",
            "account-ledger",
            "period-summary",
            "financial-position",
            "income-statement",
            "changes-in-equity"),
        commandNames(payload.path("commands").path("query")));
    assertEquals(
        "[\"json\",\"human\"]",
        commandDescriptor(payload.path("commands").path("discovery"), "version")
            .path("outputModes")
            .toString());
    JsonNode trialBalance =
        commandDescriptor(payload.path("commands").path("query"), "trial-balance");
    assertEquals("[\"json\",\"human\",\"csv\"]", trialBalance.path("outputModes").toString());
    assertEquals("pdf", trialBalance.path("artifactOutputs").get(0).path("format").stringValue());
    assertEquals(
        "--pdf-out <path>",
        trialBalance.path("artifactOutputs").get(0).path("option").stringValue());
  }

  private static void assertCapabilitiesRequestShapes(JsonNode payload) {
    JsonNode fullContract = payload.path("fullContract");
    JsonNode requestShapes = fullContract.path("requestShapes");
    JsonNode preflight = fullContract.path("preflight");
    JsonNode currencyModel = fullContract.path("currencyModel");
    assertTrue(requestShapes.has("postEntry"));
    assertTrue(requestShapes.has("declareAccount"));
    assertEquals(
        "https://json-schema.org/draft/2020-12/schema",
        requestShapes.path("schemaDialect").stringValue());
    assertEquals("advisory", preflight.path("semantics").stringValue());
    assertEquals("not-guaranteed", preflight.path("commitGuarantee").stringValue());
    assertEquals("single-functional-currency-per-book", currencyModel.path("scope").stringValue());
    assertEquals("not-supported", currencyModel.path("multiCurrencyStatus").stringValue());
    assertTrue(requestShapes.path("postEntry").path("topLevelFields").isArray());
    assertEquals(
        "entryKind",
        requestShapes.path("postEntry").path("topLevelFields").get(0).path("name").stringValue());
    assertEquals(
        RequestFieldPresence.REQUIRED.wireValue(),
        requestShapes
            .path("postEntry")
            .path("topLevelFields")
            .get(0)
            .path("presence")
            .stringValue());
    assertTrue(requestShapes.path("postEntry").path("schema").path("oneOf").isArray());
    assertEquals(
        "array",
        requestShapes
            .path("ledgerPlan")
            .path("schema")
            .path("properties")
            .path("steps")
            .path("type")
            .stringValue());
    assertEquals(
        "conditional",
        descriptorField(requestShapes.path("ledgerPlan").path("stepFields"), "posting")
            .path("presence")
            .stringValue());
    assertEquals(
        "conditional",
        descriptorField(requestShapes.path("ledgerPlan").path("queryFields"), "accountCode")
            .path("presence")
            .stringValue());
  }

  private static void assertEnvironmentRuntimeContract(JsonNode payload) {
    assertEquals(
        ProtocolCatalog.storageDriver().wireValue(),
        payload.path("storage").path("storageDriver").stringValue());
    assertEquals(
        ProtocolCatalog.bookProtectionMode().wireValue(),
        payload.path("storage").path("bookProtectionMode").stringValue());
    assertEquals(
        ProtocolCatalog.protectedBookFormat().cipher().wireValue(),
        payload.path("storage").path("defaultProtectedBookFormat").path("cipher").stringValue());
    assertEquals(
        ProtocolCatalog.protectedBookFormat().legacyMode(),
        payload
            .path("storage")
            .path("defaultProtectedBookFormat")
            .path("legacyMode")
            .booleanValue());
    assertEquals(
        ProtocolCatalog.protectedBookFormat().pageSize(),
        payload.path("storage").path("defaultProtectedBookFormat").path("pageSize").intValue());
    assertEquals(
        ProtocolCatalog.protectedBookFormat().reservedBytes(),
        payload
            .path("storage")
            .path("defaultProtectedBookFormat")
            .path("reservedBytes")
            .intValue());
    assertEquals(
        ProtocolCatalog.sqliteLibraryMode().wireValue(),
        payload.path("sqlite").path("libraryMode").stringValue());
    assertEquals(
        ProtocolCatalog.publicCliDistribution().wireValue(),
        payload.path("distribution").path("publicCliDistribution").stringValue());
    assertEquals(
        FinGrindCli.runtimeDistribution(),
        payload.path("distribution").path("runtimeDistribution").stringValue());
    assertEquals(
        ProtocolCatalog.supportedPublicCliBundleTargets().stream()
            .map(dev.erst.fingrind.contract.protocol.PublicCliBundleTarget::wireValue)
            .toList(),
        readTextArray(payload.path("distribution").path("supportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliBundleTargets().stream()
            .map(dev.erst.fingrind.contract.protocol.PublicCliBundleTarget::wireValue)
            .toList(),
        readTextArray(payload.path("distribution").path("unsupportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.sqliteBundleHomeSystemProperty(),
        payload.path("sqlite").path("bundleHomeSystemProperty").stringValue());
    assertEquals(
        ProtocolCatalog.requiredSqliteCompileOptions(),
        readTextArray(payload.path("sqlite").path("requiredCompileOptions")));
    assertEquals(
        ProtocolCatalog.forbiddenSqliteCompileOptions(),
        readTextArray(payload.path("sqlite").path("forbiddenCompileOptions")));
    assertTrue(payload.path("sqlite").path("requiresSecureMemorySupport").booleanValue());
    assertEquals(
        "verified",
        payload.path("sqlite").path("runtime").path("compileOptionsVerification").stringValue());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        payload.path("sqlite").path("requiredSqliteSourceId").stringValue());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
        payload.path("sqlite").path("requiredSqlite3mcVersion").stringValue());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
        payload.path("sqlite").path("runtime").path("loadedSqlite3mcVersion").stringValue());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        payload.path("sqlite").path("runtime").path("loadedSqliteSourceId").stringValue());
    assertFalse(
        payload.path("sqlite").path("runtime").path("loadedLibraryPath").stringValue().isBlank());
    assertFalse(
        payload.path("sqlite").path("runtime").path("runtimeProvenance").stringValue().isBlank());
    String runtimeProvenance =
        payload.path("sqlite").path("runtime").path("runtimeProvenance").stringValue();
    assertEquals(
        SqliteRuntimeTrustBasis.fromProvenance(
                SqliteRuntimeProvenance.fromWireValue(runtimeProvenance))
            .wireValue(),
        payload.path("sqlite").path("runtime").path("runtimeTrustBasis").stringValue());
  }

  private static void assertCapabilitiesRequestInput(JsonNode payload) {
    assertEquals(
        "[\"--book-key-file\",\"--book-passphrase-stdin\",\"--book-passphrase-prompt\"]",
        payload.path("requestInput").path("bookPassphraseOptions").toString());
    assertEquals("--output", payload.path("requestInput").path("outputOption").stringValue());
    assertFalse(payload.path("requestInput").has("queryOutputModes"));
    assertTrue(
        payload
            .path("requestInput")
            .path("requestDocumentSemantics")
            .toString()
            .contains("duplicate JSON object keys are rejected"));
  }

  private static void assertCapabilitiesResponseModel(JsonNode payload) {
    JsonNode responseModel = payload.path("fullContract").path("responseModel");
    assertTrue(responseModel.path("rejections").isArray());
    assertFalse(responseModel.has("rejectionCodes"));
    assertTrue(responseModel.path("errorDescriptors").isArray());
    assertTrue(responseModel.path("errorDescriptors").toString().contains("invalid-page-cursor"));
    assertTrue(
        responseModel
            .path("errorDescriptors")
            .toString()
            .contains("protected-book-verification-failed"));
    JsonNode protectedBookVerificationDescriptor =
        java.util.stream.StreamSupport.stream(
                responseModel.path("errorDescriptors").spliterator(), false)
            .filter(
                descriptor ->
                    "protected-book-verification-failed"
                        .equals(descriptor.path("code").stringValue()))
            .findFirst()
            .orElseThrow();
    assertEquals(6, protectedBookVerificationDescriptor.path("exitCode").intValue());
    assertTrue(
        responseModel.path("errorDescriptors").toString().contains("managed-runtime-failure"));
    assertTrue(
        responseModel.path("errorDescriptors").toString().contains("storage-runtime-failure"));
    assertTrue(responseModel.path("errorDescriptors").toString().contains("pdf-export-failure"));
  }

  @Test
  void run_returnsVersion() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"version", "--output", "json"});
    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"application\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"version\""));
  }

  @Test
  void environmentDescriptor_reportsUnavailableRuntimeWhenSqliteProbeFails() {
    EnvironmentDescriptor environmentDescriptor =
        FinGrindCli.environmentDescriptor(
            new SqliteRuntime.Probe(
                ProtocolCatalog.sqliteLibraryMode().wireValue(),
                SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
                SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                "managed sqlite unavailable",
                null),
            FinGrindCli.SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION);
    assertEquals(
        ProtocolCatalog.sourceCheckoutRuntimeDistribution(),
        environmentDescriptor.distribution().runtimeDistribution());
    assertEquals(ProtocolCatalog.storageDriver(), environmentDescriptor.storage().storageDriver());
    assertEquals(ProtocolCatalog.storageEngine(), environmentDescriptor.storage().storageEngine());
    assertEquals(
        ProtocolCatalog.bookProtectionMode(), environmentDescriptor.storage().bookProtectionMode());
    assertEquals(
        ProtocolCatalog.protectedBookFormat(),
        environmentDescriptor.storage().defaultProtectedBookFormat());
    assertEquals(ProtocolCatalog.sqliteLibraryMode(), environmentDescriptor.sqlite().libraryMode());
    assertEquals(
        ProtocolCatalog.publicCliDistribution(),
        environmentDescriptor.distribution().publicCliDistribution());
    assertEquals(
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        environmentDescriptor.distribution().supportedPublicCliBundleTargets());
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliBundleTargets(),
        environmentDescriptor.distribution().unsupportedPublicCliBundleTargets());
    assertEquals(
        ProtocolCatalog.sourceCheckoutJava(),
        environmentDescriptor.distribution().sourceCheckoutJava());
    assertEquals(
        ProtocolCatalog.sqliteBundleHomeSystemProperty(),
        environmentDescriptor.sqlite().bundleHomeSystemProperty());
    assertEquals(
        ProtocolCatalog.requiredSqliteCompileOptions(),
        environmentDescriptor.sqlite().requiredCompileOptions());
    assertEquals(
        ProtocolCatalog.forbiddenSqliteCompileOptions(),
        environmentDescriptor.sqlite().forbiddenCompileOptions());
    assertTrue(environmentDescriptor.sqlite().requiresSecureMemorySupport());
    EnvironmentSqliteDescriptor.UnavailableRuntime unavailableRuntime =
        (EnvironmentSqliteDescriptor.UnavailableRuntime) environmentDescriptor.sqlite().runtime();
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        unavailableRuntime.compileOptionsVerification());
    assertEquals(
        SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
        environmentDescriptor.sqlite().requiredMinimumSqliteVersion());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
        environmentDescriptor.sqlite().requiredSqlite3mcVersion());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        environmentDescriptor.sqlite().requiredSqliteSourceId());
    assertEquals(SqliteRuntimeStatus.UNAVAILABLE, unavailableRuntime.status());
    assertEquals("managed sqlite unavailable", unavailableRuntime.runtimeIssue());
  }

  @Test
  void run_generatesBookKeyFileWithNonSecretMetadata() throws IOException {
    Path keyFilePath = tempDirectory.resolve("secrets").resolve("entity.book-key");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode =
        cli.run(new String[] {"generate-book-key-file", "--book-key-file", keyFilePath.toString()});
    assertEquals(0, exitCode);
    assertTrue(Files.isRegularFile(keyFilePath));
    JsonNode payload = new ObjectMapper().readTree(outputStream.toByteArray()).path("payload");
    assertGeneratedKeyFileIsSecure(keyFilePath, payload.path("permissions").stringValue());
    assertEquals(
        CliPublicPaths.normalizedValue(keyFilePath), payload.path("bookKeyFile").stringValue());
    assertEquals("base64url-no-padding", payload.path("encoding").stringValue());
    assertEquals(256, payload.path("entropyBits").asInt());
    assertFalse(
        outputStream.toString(StandardCharsets.UTF_8).contains(Files.readString(keyFilePath)));
  }

  @Test
  void run_reportsDeterministicFailureWhenGeneratedKeyFileAlreadyExists() throws IOException {
    Path keyFilePath = tempDirectory.resolve("secrets").resolve("existing.book-key");
    writeSecureKey(keyFilePath, "already-present");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode =
        cli.run(new String[] {"generate-book-key-file", "--book-key-file", keyFilePath.toString()});
    assertEquals(7, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.BOOK_KEY_FILE_ALREADY_EXISTS.code(),
        failureEnvelope.path("code").stringValue());
    assertTrue(failureEnvelope.path("message").stringValue().contains("already exists"));
  }

  @Test
  void run_printsRequestTemplateWithoutCallerSuppliedCommitFields() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"print-request-template"});
    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"2026-01-15\""));
    assertTrue(json.contains("\"effectiveDate\""));
    assertTrue(json.contains("\"provenance\""));
    assertFalse(json.contains("recordedAt"));
    assertFalse(json.contains("sourceChannel"));
  }

  @Test
  void run_printsPlanTemplateForAgentWorkflows() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"print-plan-template"});
    assertEquals(0, exitCode);
    JsonNode json =
        assertDoesNotThrow(
            () -> new ObjectMapper().readTree(outputStream.toString(StandardCharsets.UTF_8)));
    assertEquals("plan-1", json.path("planId").stringValue());
    assertFalse(json.has("executionPolicy"));
    assertEquals("initialize-book", json.path("steps").get(0).path("stepId").stringValue());
    assertEquals("assert-cash-balance", json.path("steps").get(4).path("stepId").stringValue());
    assertEquals(
        "assert-account-balance",
        json.path("steps").get(4).path("assertion").path("kind").stringValue());
    assertTrue(json.path("steps").get(4).has("assertion"));
    assertFalse(json.path("steps").get(4).has("accountBalanceAssertion"));
  }

  private static JsonNode descriptorField(JsonNode fields, String fieldName) {
    for (JsonNode field : fields) {
      if (fieldName.equals(field.path("name").stringValue())) {
        return field;
      }
    }
    throw new AssertionError("Missing descriptor field: " + fieldName);
  }

  @Test
  void run_doesNotTouchWorkflowForDiscoveryCommands() {
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(accountPage(List.of(), 50, Optional.empty())),
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
