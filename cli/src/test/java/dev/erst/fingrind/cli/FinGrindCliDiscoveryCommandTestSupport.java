package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared assertions and helper logic for split CLI discovery command tests. */
abstract class FinGrindCliDiscoveryCommandTestSupport extends FinGrindCliTestSupport {
  protected static List<String> commandNames(JsonNode commands) {
    List<String> names = new ArrayList<>();
    commands.forEach(command -> names.add(command.path("name").stringValue()));
    return List.copyOf(names);
  }

  protected static boolean containsText(JsonNode node, String expected) {
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

  protected static boolean containsCollapsedText(String text, String expected) {
    return collapseWhitespace(text).contains(collapseWhitespace(expected));
  }

  private static String collapseWhitespace(String text) {
    return text.replaceAll("\\s+", " ").trim();
  }

  protected final void assertRuntimeSpecificHelpSurface(String runtimeDistribution) {
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
      int helpExitCode = helpCli.run(new String[] {"help", "--output", "text"});
      int commandHelpExitCode =
          commandHelpCli.run(new String[] {"help", "open-book", "--output", "text"});
      int failureExitCode = failureCli.run(jsonArguments("post-entry", "--bogus"));
      assertEquals(0, helpExitCode);
      assertEquals(0, commandHelpExitCode);
      assertEquals(1, failureExitCode);
      String help = helpOutputStream.toString(StandardCharsets.UTF_8);
      String commandHelp = commandHelpOutputStream.toString(StandardCharsets.UTF_8);
      String launcher =
          CliInvocationText.launcherCommandFor(
              runtimeDistribution, System.getProperty("os.name", ""));
      assertTrue(help.contains("Quick Start"), help);
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

  protected static JsonNode commandDescriptor(JsonNode commands, String operationId) {
    for (JsonNode command : commands) {
      if (operationId.equals(command.path("name").stringValue())) {
        return command;
      }
    }
    throw new AssertionError("Missing command descriptor: " + operationId);
  }

  protected static void assertCapabilitiesCommandCatalog(JsonNode payload) {
    assertEquals(
        List.of(
            "generate-book-key-file",
            "generate-attestation-key-file",
            "open-book",
            "rekey-book",
            "backup-book",
            "restore-book",
            "enroll-key",
            "rollover-key",
            "revoke-key",
            "alter-policy",
            "declare-account",
            "amend-account",
            "retire-account",
            "declare-tax-registration",
            "interim-result-sweep",
            "fiscal-year-close"),
        commandNames(payload.path("commands").path("administration")));
    assertEquals(
        List.of(
            "inspect-attestation-key-file",
            "inspect-book",
            "verify-book",
            "attestation-review",
            "export-attestation-receipt",
            "verify-receipt",
            "list-accounts",
            "list-tax-registrations",
            "tax-obligation",
            "get-posting",
            "list-postings",
            "account-balance",
            "trial-balance",
            "account-ledger",
            "period-summary",
            "financial-position",
            "inventory-valuation",
            "accrual-cutoff-schedule",
            "fixed-asset-register",
            "financing-register",
            "realized-foreign-exchange-register",
            "latvian-payroll-register",
            "income-statement",
            "cash-flow-statement",
            "changes-in-equity"),
        commandNames(payload.path("commands").path("query")));
    assertEquals(
        "[\"json\",\"text\"]",
        commandDescriptor(payload.path("commands").path("discovery"), "version")
            .path("outputModes")
            .toString());
    JsonNode trialBalance =
        commandDescriptor(payload.path("commands").path("query"), "trial-balance");
    assertEquals("[\"json\",\"text\",\"csv\"]", trialBalance.path("outputModes").toString());
    assertEquals("pdf", trialBalance.path("artifactOutputs").get(0).path("format").stringValue());
    assertEquals(
        "--pdf-out <path>",
        trialBalance.path("artifactOutputs").get(0).path("option").stringValue());
  }

  protected static void assertCapabilitiesRequestShapes(JsonNode payload) {
    JsonNode fullContract = payload.path("fullContract");
    JsonNode requestShapes = fullContract.path("requestShapes");
    JsonNode preflight = fullContract.path("preflight");
    JsonNode currencyModel = fullContract.path("currencyModel");
    assertTrue(requestShapes.has("bookkeepingEntry"));
    assertTrue(requestShapes.has("declareAccount"));
    assertTrue(
        requestShapes
            .path("declareAccount")
            .path("schema")
            .path("properties")
            .has("cashFlowAssetClassification"));
    assertTrue(
        requestShapes
            .path("declareAccount")
            .path("schema")
            .path("properties")
            .has("unitOfMeasure"));
    assertEquals(
        "https://json-schema.org/draft/2020-12/schema",
        requestShapes.path("schemaDialect").stringValue());
    assertEquals("advisory", preflight.path("semantics").stringValue());
    assertEquals("not-guaranteed", preflight.path("commitGuarantee").stringValue());
    assertEquals("single-functional-currency-per-book", currencyModel.path("scope").stringValue());
    assertEquals(
        "owned-foreign-exchange-only", currencyModel.path("multiCurrencyStatus").stringValue());
    assertTrue(requestShapes.path("bookkeepingEntry").path("topLevelFields").isArray());
    assertEquals(
        "entryKind",
        requestShapes
            .path("bookkeepingEntry")
            .path("topLevelFields")
            .get(0)
            .path("name")
            .stringValue());
    assertEquals(
        RequestFieldPresence.REQUIRED.wireValue(),
        requestShapes
            .path("bookkeepingEntry")
            .path("topLevelFields")
            .get(0)
            .path("presence")
            .stringValue());
    assertTrue(requestShapes.path("bookkeepingEntry").path("schema").path("oneOf").isArray());
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
    JsonNode journalSemantics =
        requestShapes.path("bookkeepingEntry").path("entryKindSemantics").get(0);
    assertEquals("DIRECT_JOURNAL", journalSemantics.path("entryKind").stringValue());
    JsonNode saleSemantics =
        requestShapes.path("bookkeepingEntry").path("entryKindSemantics").get(1);
    assertEquals("SALE_SETTLED", saleSemantics.path("entryKind").stringValue());
    assertEquals("enumerated", saleSemantics.path("sourceDocumentTypeMode").stringValue());
    assertTrue(saleSemantics.path("acceptedSourceDocumentTypes").isArray());
    assertEquals(
        1,
        requestShapes
            .path("bookkeepingEntry")
            .path("evidenceRequirement")
            .path("minimumSourceDocuments")
            .intValue());
    assertEquals(
        "sourceDocumentId",
        saleSemantics.path("requiredSourceDocumentFields").get(0).stringValue());
  }

  protected static void assertEnvironmentRuntimeContract(JsonNode payload) {
    assertEquals(
        ProtocolCatalog.runtime().storageDriver().wireValue(),
        payload.path("storage").path("storageDriver").stringValue());
    assertEquals(
        ProtocolCatalog.runtime().bookProtectionMode().wireValue(),
        payload.path("storage").path("bookProtectionMode").stringValue());
    assertEquals(
        ProtocolCatalog.runtime().protectedBookFormat().cipher().wireValue(),
        payload.path("storage").path("defaultProtectedBookFormat").path("cipher").stringValue());
    assertEquals(
        ProtocolCatalog.runtime().protectedBookFormat().legacyMode(),
        payload
            .path("storage")
            .path("defaultProtectedBookFormat")
            .path("legacyMode")
            .booleanValue());
    assertEquals(
        ProtocolCatalog.runtime().protectedBookFormat().pageSize(),
        payload.path("storage").path("defaultProtectedBookFormat").path("pageSize").intValue());
    assertEquals(
        ProtocolCatalog.runtime().protectedBookFormat().reservedBytes(),
        payload
            .path("storage")
            .path("defaultProtectedBookFormat")
            .path("reservedBytes")
            .intValue());
    assertEquals(
        ProtocolCatalog.runtime().sqliteLibraryMode().wireValue(),
        payload.path("sqlite").path("libraryMode").stringValue());
    assertEquals(
        ProtocolCatalog.distribution().publicCliDistribution().wireValue(),
        payload.path("publication").path("publicCliDistribution").stringValue());
    assertEquals(
        FinGrindCli.runtimeDistribution(),
        payload.path("runtime").path("runtimeDistribution").stringValue());
    assertEquals(
        ProtocolCatalog.distribution().supportedPublicCliBundleTargets().stream()
            .map(dev.erst.fingrind.contract.protocol.PublicCliBundleTarget::wireValue)
            .toList(),
        readTextArray(payload.path("publication").path("supportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets().stream()
            .map(dev.erst.fingrind.contract.protocol.PublicCliBundleTarget::wireValue)
            .toList(),
        readTextArray(payload.path("publication").path("unsupportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.runtime().sqliteBundleHomeSystemProperty(),
        payload.path("sqlite").path("bundleHomeSystemProperty").stringValue());
    assertEquals(
        ProtocolCatalog.managedSqlite().requiredCompileOptions(),
        readTextArray(payload.path("sqlite").path("requiredCompileOptions")));
    assertEquals(
        ProtocolCatalog.managedSqlite().forbiddenCompileOptions(),
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

  protected static void assertCapabilitiesRequestInput(JsonNode payload) {
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

  protected static void assertCapabilitiesResponseModel(JsonNode payload) {
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
    assertTrue(responseModel.path("errorDescriptors").toString().contains("internal-defect"));
    assertTrue(responseModel.path("errorDescriptors").toString().contains("internal-error"));
    assertTrue(
        responseModel.path("errorDescriptors").toString().contains("managed-runtime-failure"));
    assertTrue(
        responseModel.path("errorDescriptors").toString().contains("storage-runtime-failure"));
    assertTrue(responseModel.path("errorDescriptors").toString().contains("pdf-export-failure"));
    JsonNode staleHeadDescriptor =
        java.util.stream.StreamSupport.stream(
                responseModel.path("errorDescriptors").spliterator(), false)
            .filter(descriptor -> "stale-head".equals(descriptor.path("code").stringValue()))
            .findFirst()
            .orElseThrow();
    assertEquals(2, staleHeadDescriptor.path("exitCode").intValue());
    List<String> staleHeadDetailFields = new ArrayList<>();
    staleHeadDescriptor
        .path("detailFields")
        .forEach(field -> staleHeadDetailFields.add(field.path("name").stringValue()));
    assertEquals(List.of("observedHead", "currentHead", "currentOrder"), staleHeadDetailFields);
  }

  protected static JsonNode descriptorField(JsonNode fields, String fieldName) {
    for (JsonNode field : fields) {
      if (fieldName.equals(field.path("name").stringValue())) {
        return field;
      }
    }
    throw new AssertionError("Missing descriptor field: " + fieldName);
  }

  protected static JsonNode descriptorByFieldValue(
      JsonNode descriptors, String fieldName, String fieldValue) {
    for (JsonNode descriptor : descriptors) {
      if (fieldValue.equals(descriptor.path(fieldName).stringValue())) {
        return descriptor;
      }
    }
    throw new AssertionError("Missing descriptor where " + fieldName + " == " + fieldValue + ".");
  }

  protected static EnvironmentDescriptor unavailableRuntimeEnvironmentDescriptor() {
    return FinGrindCli.environmentDescriptor(
        new SqliteRuntime.Probe(
            ProtocolCatalog.runtime().sqliteLibraryMode().wireValue(),
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
  }

  protected static void assertUnavailableRuntimeEnvironmentDescriptor(
      EnvironmentDescriptor environmentDescriptor) {
    assertEquals(
        ProtocolCatalog.distribution().sourceCheckoutRuntimeDistribution(),
        environmentDescriptor.runtime().runtimeDistribution());
    assertEquals(
        ProtocolCatalog.runtime().storageDriver(), environmentDescriptor.storage().storageDriver());
    assertEquals(
        ProtocolCatalog.runtime().storageEngine(), environmentDescriptor.storage().storageEngine());
    assertEquals(
        ProtocolCatalog.runtime().bookProtectionMode(),
        environmentDescriptor.storage().bookProtectionMode());
    assertEquals(
        ProtocolCatalog.runtime().protectedBookFormat(),
        environmentDescriptor.storage().defaultProtectedBookFormat());
    assertEquals(
        ProtocolCatalog.runtime().sqliteLibraryMode(),
        environmentDescriptor.sqlite().libraryMode());
    assertEquals(
        ProtocolCatalog.distribution().publicCliDistribution(),
        environmentDescriptor.publication().publicCliDistribution());
    assertEquals(
        ProtocolCatalog.distribution().supportedPublicCliBundleTargets(),
        environmentDescriptor.publication().supportedPublicCliBundleTargets());
    assertEquals(
        ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets(),
        environmentDescriptor.publication().unsupportedPublicCliBundleTargets());
    assertEquals(
        ProtocolCatalog.distribution().sourceCheckoutJava(),
        environmentDescriptor.publication().sourceCheckoutJava());
    assertEquals(
        ProtocolCatalog.runtime().sqliteBundleHomeSystemProperty(),
        environmentDescriptor.sqlite().bundleHomeSystemProperty());
    assertEquals(
        ProtocolCatalog.managedSqlite().requiredCompileOptions(),
        environmentDescriptor.sqlite().requiredCompileOptions());
    assertEquals(
        ProtocolCatalog.managedSqlite().forbiddenCompileOptions(),
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
}
