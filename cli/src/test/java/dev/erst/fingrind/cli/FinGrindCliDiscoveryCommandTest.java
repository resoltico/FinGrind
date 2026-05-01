package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EnvironmentDescriptor;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.RequestFieldPresence;
import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
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
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
@NullUnmarked
class FinGrindCliDiscoveryCommandTest extends FinGrindCliTestSupport {
  @Test
  void run_returnsHelpWhenArgumentsAreEmpty() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[0]);

    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("FinGrind Help"));
    assertTrue(help.contains("open-book"));
    assertTrue(help.contains("declare-account"));
    assertTrue(help.contains("list-accounts"));
    assertTrue(
        help.contains(
            CliInvocationText.commandExample(OperationId.GENERATE_BOOK_KEY_FILE)
                + " --book-key-file ./acme.book-key"));
    assertTrue(help.contains("Write: ./declare-account-cash.json"));
    assertTrue(help.contains("\"accountCode\": \"1000\""));
    assertTrue(help.contains("--request-file ./declare-account-cash.json"));
    assertTrue(help.contains("Write: ./declare-account-revenue.json"));
    assertTrue(help.contains("\"accountCode\": \"2000\""));
    assertTrue(help.contains("--request-file ./declare-account-revenue.json"));
    assertFalse(help.contains("--request-file ./cash.json"));
    assertFalse(help.contains("--request-file ./revenue.json"));
    assertTrue(help.contains("Note: Replace scaffold placeholders such as effectiveDate"));
    assertTrue(help.contains("Note: Use a fresh provenance.idempotencyKey"));
  }

  @Test
  void run_returnsScopedHelpForExplicitHelpTopic() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"help", "post-entry"});

    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("Command"));
    assertTrue(help.contains("Usage"));
    assertTrue(help.contains("Examples"));
    assertTrue(help.contains("post-entry"));
    assertTrue(help.contains("--request-file <path|->"));
  }

  @Test
  void run_returnsScopedHelpForCommandHelpAlias() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"post-entry", "--help"});

    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("post-entry"));
    assertTrue(help.contains("Usage"));
    assertTrue(help.contains("Examples"));
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
          new ObjectMapper().readTree(failureOutputStream.toString(StandardCharsets.UTF_8));
      assertEquals("Unsupported argument: --bogus", failurePayload.path("message").stringValue());
      assertEquals(
          "Run '" + bundleLauncher + " help post-entry' to inspect the supported command syntax.",
          failurePayload.path("hint").stringValue());
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
    assertRuntimeSpecificHelpSurface(
        FinGrindCli.SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION,
        "./cli/build/install/cli-shadow/bin/cli",
        "Source Checkout Launcher");
  }

  @Test
  void run_rewritesDirectJavaHelpToTheDeveloperJarSurface() {
    assertRuntimeSpecificHelpSurface(
        FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION,
        ProtocolCatalog.directJavaLauncherCommand(false),
        "Developer Raw JAR");
  }

  @Test
  void run_rewritesContainerHelpToTheDockerSurface() {
    assertRuntimeSpecificHelpSurface(
        FinGrindCli.CONTAINER_RUNTIME_DISTRIBUTION,
        "docker run --rm -v <host-workdir>:/workspace -w /workspace <container-image>",
        "Container Image");
  }

  @Test
  void run_returnsCapabilities() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"capabilities", "--output", "json"});

    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    JsonNode payload = new ObjectMapper().readTree(json).path("payload");
    assertTrue(json.contains("\"administration\""));
    assertTrue(json.contains("\"query\""));
    assertTrue(json.contains("\"administration-book-not-initialized\""));
    assertTrue(json.contains("\"query-book-not-initialized\""));
    assertTrue(json.contains("\"posting-book-not-initialized\""));
    assertTrue(json.contains("\"account-normal-balance-conflict\""));
    assertTrue(json.contains("\"posting-not-found\""));
    assertCapabilitiesCommandCatalog(payload);
    assertCapabilitiesRequestShapes(payload);
    assertCapabilitiesRuntimeContract(payload);
    assertCapabilitiesRequestInput(payload);
    assertCapabilitiesResponseModel(payload);
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

  private void assertRuntimeSpecificHelpSurface(
      String runtimeDistribution, String expectedLauncher, String expectedQuickStartTitle) {
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
      ByteArrayOutputStream failureOutputStream = new ByteArrayOutputStream();
      FinGrindCli failureCli =
          cli(
              new ByteArrayInputStream(new byte[0]),
              utf8PrintStream(failureOutputStream),
              fixedClock());

      int helpExitCode = helpCli.run(new String[0]);
      int failureExitCode = failureCli.run(new String[] {"post-entry", "--bogus"});

      assertEquals(0, helpExitCode);
      assertEquals(1, failureExitCode);
      String help = helpOutputStream.toString(StandardCharsets.UTF_8);
      assertTrue(help.contains(expectedQuickStartTitle), help);
      assertTrue(help.contains(expectedLauncher), help);
      JsonNode failurePayload =
          new ObjectMapper().readTree(failureOutputStream.toString(StandardCharsets.UTF_8));
      assertEquals(
          "Run '" + expectedLauncher + " help post-entry' to inspect the supported command syntax.",
          failurePayload.path("hint").stringValue());
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

  private static void assertCapabilitiesCommandCatalog(JsonNode payload) {
    assertEquals(
        List.of("generate-book-key-file", "open-book", "rekey-book", "declare-account"),
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
            "period-summary"),
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
    assertTrue(payload.path("requestShapes").has("postEntry"));
    assertTrue(payload.path("requestShapes").has("declareAccount"));
    assertEquals(
        "https://json-schema.org/draft/2020-12/schema",
        payload.path("requestShapes").path("schemaDialect").stringValue());
    assertEquals("advisory", payload.path("preflight").path("semantics").stringValue());
    assertEquals("not-guaranteed", payload.path("preflight").path("commitGuarantee").stringValue());
    assertEquals(
        "single-currency-per-entry", payload.path("currencyModel").path("scope").stringValue());
    assertEquals(
        "not-supported", payload.path("currencyModel").path("multiCurrencyStatus").stringValue());
    assertTrue(payload.path("requestShapes").path("postEntry").path("topLevelFields").isArray());
    assertEquals(
        "effectiveDate",
        payload
            .path("requestShapes")
            .path("postEntry")
            .path("topLevelFields")
            .get(0)
            .path("name")
            .stringValue());
    assertEquals(
        RequestFieldPresence.REQUIRED.wireValue(),
        payload
            .path("requestShapes")
            .path("postEntry")
            .path("topLevelFields")
            .get(0)
            .path("presence")
            .stringValue());
    assertEquals(
        "object",
        payload.path("requestShapes").path("postEntry").path("schema").path("type").stringValue());
    assertEquals(
        "array",
        payload
            .path("requestShapes")
            .path("ledgerPlan")
            .path("schema")
            .path("properties")
            .path("steps")
            .path("type")
            .stringValue());
    assertEquals(
        "conditional",
        descriptorField(
                payload.path("requestShapes").path("ledgerPlan").path("stepFields"), "posting")
            .path("presence")
            .stringValue());
    assertEquals(
        "conditional",
        descriptorField(
                payload.path("requestShapes").path("ledgerPlan").path("queryFields"), "accountCode")
            .path("presence")
            .stringValue());
  }

  private static void assertCapabilitiesRuntimeContract(JsonNode payload) {
    assertEquals(
        ProtocolCatalog.storageDriver().wireValue(),
        payload.path("environment").path("storage").path("storageDriver").stringValue());
    assertEquals(
        ProtocolCatalog.bookProtectionMode().wireValue(),
        payload.path("environment").path("storage").path("bookProtectionMode").stringValue());
    assertEquals(
        ProtocolCatalog.protectedBookFormat().cipher().wireValue(),
        payload
            .path("environment")
            .path("storage")
            .path("defaultProtectedBookFormat")
            .path("cipher")
            .stringValue());
    assertEquals(
        ProtocolCatalog.protectedBookFormat().legacyMode(),
        payload
            .path("environment")
            .path("storage")
            .path("defaultProtectedBookFormat")
            .path("legacyMode")
            .booleanValue());
    assertEquals(
        ProtocolCatalog.protectedBookFormat().pageSize(),
        payload
            .path("environment")
            .path("storage")
            .path("defaultProtectedBookFormat")
            .path("pageSize")
            .intValue());
    assertEquals(
        ProtocolCatalog.protectedBookFormat().reservedBytes(),
        payload
            .path("environment")
            .path("storage")
            .path("defaultProtectedBookFormat")
            .path("reservedBytes")
            .intValue());
    assertEquals(
        ProtocolCatalog.sqliteLibraryMode().wireValue(),
        payload.path("environment").path("sqlite").path("libraryMode").stringValue());
    assertEquals(
        ProtocolCatalog.publicCliDistribution().wireValue(),
        payload
            .path("environment")
            .path("distribution")
            .path("publicCliDistribution")
            .stringValue());
    assertEquals(
        FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION,
        payload.path("environment").path("distribution").path("runtimeDistribution").stringValue());
    assertEquals(
        ProtocolCatalog.supportedPublicCliBundleTargets().stream()
            .map(dev.erst.fingrind.contract.protocol.PublicCliBundleTarget::wireValue)
            .toList(),
        readTextArray(
            payload
                .path("environment")
                .path("distribution")
                .path("supportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliBundleTargets().stream()
            .map(dev.erst.fingrind.contract.protocol.PublicCliBundleTarget::wireValue)
            .toList(),
        readTextArray(
            payload
                .path("environment")
                .path("distribution")
                .path("unsupportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.sqliteLibraryEnvironmentVariable(),
        payload
            .path("environment")
            .path("sqlite")
            .path("libraryEnvironmentVariable")
            .stringValue());
    assertEquals(
        ProtocolCatalog.sqliteBundleHomeSystemProperty(),
        payload.path("environment").path("sqlite").path("bundleHomeSystemProperty").stringValue());
    assertEquals(
        ProtocolCatalog.requiredSqliteCompileOptions(),
        readTextArray(payload.path("environment").path("sqlite").path("requiredCompileOptions")));
    assertEquals(
        "verified",
        payload
            .path("environment")
            .path("sqlite")
            .path("compileOptionsVerification")
            .stringValue());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        payload.path("environment").path("sqlite").path("requiredSqliteSourceId").stringValue());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
        payload.path("environment").path("sqlite").path("requiredSqlite3mcVersion").stringValue());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
        payload.path("environment").path("sqlite").path("loadedSqlite3mcVersion").stringValue());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        payload.path("environment").path("sqlite").path("loadedSqliteSourceId").stringValue());
    assertFalse(
        payload
            .path("environment")
            .path("sqlite")
            .path("loadedLibraryPath")
            .stringValue()
            .isBlank());
    assertFalse(
        payload
            .path("environment")
            .path("sqlite")
            .path("runtimeProvenance")
            .stringValue()
            .isBlank());
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
    assertTrue(payload.path("responseModel").path("rejections").isArray());
    assertFalse(payload.path("responseModel").has("rejectionCodes"));
    assertTrue(payload.path("responseModel").path("errorDescriptors").isArray());
    assertTrue(
        payload
            .path("responseModel")
            .path("errorDescriptors")
            .toString()
            .contains("invalid-page-cursor"));
    assertTrue(
        payload
            .path("responseModel")
            .path("errorDescriptors")
            .toString()
            .contains("book-authentication-failed"));
    assertTrue(
        payload
            .path("responseModel")
            .path("errorDescriptors")
            .toString()
            .contains("managed-runtime-failure"));
    assertTrue(
        payload
            .path("responseModel")
            .path("errorDescriptors")
            .toString()
            .contains("storage-runtime-failure"));
    assertTrue(
        payload
            .path("responseModel")
            .path("errorDescriptors")
            .toString()
            .contains("pdf-export-failure"));
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
                "managed sqlite unavailable"),
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
        ProtocolCatalog.sqliteLibraryEnvironmentVariable(),
        environmentDescriptor.sqlite().libraryEnvironmentVariable());
    assertEquals(
        ProtocolCatalog.sqliteBundleHomeSystemProperty(),
        environmentDescriptor.sqlite().bundleHomeSystemProperty());
    assertEquals(
        ProtocolCatalog.requiredSqliteCompileOptions(),
        environmentDescriptor.sqlite().requiredCompileOptions());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        environmentDescriptor.sqlite().compileOptionsVerification());
    assertEquals(
        SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
        environmentDescriptor.sqlite().requiredMinimumSqliteVersion());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
        environmentDescriptor.sqlite().requiredSqlite3mcVersion());
    assertEquals(
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        environmentDescriptor.sqlite().requiredSqliteSourceId());
    assertEquals(SqliteRuntimeStatus.UNAVAILABLE, environmentDescriptor.sqlite().runtimeStatus());
    assertEquals("managed sqlite unavailable", environmentDescriptor.sqlite().runtimeIssue());
    assertNull(environmentDescriptor.sqlite().runtimeProvenance());
    assertNull(environmentDescriptor.sqlite().loadedLibraryPath());
    assertNull(environmentDescriptor.sqlite().loadedSqliteVersion());
    assertNull(environmentDescriptor.sqlite().loadedSqlite3mcVersion());
    assertNull(environmentDescriptor.sqlite().loadedSqliteSourceId());
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
        keyFilePath.toAbsolutePath().normalize().toString(),
        payload.path("bookKeyFile").stringValue());
    assertEquals("base64url-no-padding", payload.path("encoding").stringValue());
    assertEquals(256, payload.path("entropyBits").asInt());
    assertFalse(
        outputStream.toString(StandardCharsets.UTF_8).contains(Files.readString(keyFilePath)));
  }

  @Test
  void run_reportsDeterministicFailureWhenGeneratedKeyFileAlreadyExists() throws IOException {
    Path keyFilePath = tempDirectory.resolve("secrets").resolve("existing.book-key");
    Files.createDirectories(keyFilePath.getParent());
    Files.writeString(keyFilePath, "already-present", StandardCharsets.UTF_8);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(new String[] {"generate-book-key-file", "--book-key-file", keyFilePath.toString()});

    assertEquals(2, exitCode);
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
    assertTrue(json.contains("\"replace-before-commit-effective-date\""));
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
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, Optional.empty())),
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
