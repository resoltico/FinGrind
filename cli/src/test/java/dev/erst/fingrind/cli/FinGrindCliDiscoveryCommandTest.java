package dev.erst.fingrind.cli;

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
import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
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
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[0]);

    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("FinGrind Help"));
    assertTrue(help.contains("open-book"));
    assertTrue(help.contains("declare-account"));
    assertTrue(help.contains("list-accounts"));
  }

  @Test
  void run_returnsCapabilities() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

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
    assertEquals(
        "[\"generate-book-key-file\",\"open-book\",\"rekey-book\",\"declare-account\"]",
        payload.path("commands").path("administration").toString());
    assertEquals(
        "[\"inspect-book\",\"list-accounts\",\"get-posting\",\"list-postings\",\"account-balance\",\"trial-balance\",\"account-ledger\",\"period-summary\"]",
        payload.path("commands").path("query").toString());
    assertTrue(payload.path("requestShapes").has("postEntry"));
    assertTrue(payload.path("requestShapes").has("declareAccount"));
    assertEquals("advisory", payload.path("preflight").path("semantics").asString());
    assertEquals("not-guaranteed", payload.path("preflight").path("commitGuarantee").asString());
    assertEquals(
        "single-currency-per-entry", payload.path("currencyModel").path("scope").asString());
    assertEquals(
        "not-supported", payload.path("currencyModel").path("multiCurrencyStatus").asString());
    assertTrue(payload.path("requestShapes").path("postEntry").path("topLevelFields").isArray());
    assertEquals(
        "effectiveDate",
        payload
            .path("requestShapes")
            .path("postEntry")
            .path("topLevelFields")
            .get(0)
            .path("name")
            .asString());
    assertEquals(
        "required",
        payload
            .path("requestShapes")
            .path("postEntry")
            .path("topLevelFields")
            .get(0)
            .path("presence")
            .asString());
    assertTrue(payload.path("responseModel").path("rejections").isArray());
    assertFalse(payload.path("responseModel").has("rejectionCodes"));
    assertEquals(
        "sqlite-ffm-sqlite3mc",
        payload.path("environment").path("storage").path("storageDriver").asString());
    assertEquals(
        "required",
        payload.path("environment").path("storage").path("bookProtectionMode").asString());
    assertEquals(
        "chacha20",
        payload.path("environment").path("storage").path("defaultBookCipher").asString());
    assertEquals(
        "managed-only", payload.path("environment").path("sqlite").path("libraryMode").asString());
    assertEquals(
        "self-contained-bundle",
        payload.path("environment").path("distribution").path("publicCliDistribution").asString());
    assertEquals(
        FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION,
        payload.path("environment").path("distribution").path("runtimeDistribution").asString());
    assertEquals(
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        readTextArray(
            payload
                .path("environment")
                .path("distribution")
                .path("supportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
        readTextArray(
            payload
                .path("environment")
                .path("distribution")
                .path("unsupportedPublicCliOperatingSystems")));
    assertEquals(
        "FINGRIND_SQLITE_LIBRARY",
        payload.path("environment").path("sqlite").path("libraryEnvironmentVariable").asString());
    assertEquals(
        "fingrind.bundle.home",
        payload.path("environment").path("sqlite").path("bundleHomeSystemProperty").asString());
    assertEquals(
        "[\"THREADSAFE=1\",\"OMIT_LOAD_EXTENSION\",\"TEMP_STORE=3\",\"SECURE_DELETE\"]",
        payload.path("environment").path("sqlite").path("requiredCompileOptions").toString());
    assertEquals(
        "verified",
        payload.path("environment").path("sqlite").path("compileOptionsVerification").asString());
    assertEquals(
        "2.3.3",
        payload.path("environment").path("sqlite").path("requiredSqlite3mcVersion").asString());
    assertEquals(
        "2.3.3",
        payload.path("environment").path("sqlite").path("loadedSqlite3mcVersion").asString());
    assertEquals(
        "[\"--book-key-file\",\"--book-passphrase-stdin\",\"--book-passphrase-prompt\"]",
        payload.path("requestInput").path("bookPassphraseOptions").toString());
    assertEquals("--output", payload.path("requestInput").path("queryOutputOption").asString());
    assertEquals(
        "[\"json\",\"human\",\"csv\"]",
        payload.path("requestInput").path("queryOutputModes").toString());
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
            .path("requestInput")
            .path("requestDocumentSemantics")
            .toString()
            .contains("duplicate JSON object keys are rejected"));
  }

  @Test
  void run_returnsVersion() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

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
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                "managed sqlite unavailable"),
            FinGrindCli.SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION);

    assertEquals(
        "source-checkout-gradle", environmentDescriptor.distribution().runtimeDistribution());
    assertEquals("sqlite-ffm-sqlite3mc", environmentDescriptor.storage().storageDriver());
    assertEquals("sqlite", environmentDescriptor.storage().storageEngine());
    assertEquals("required", environmentDescriptor.storage().bookProtectionMode());
    assertEquals("chacha20", environmentDescriptor.storage().defaultBookCipher());
    assertEquals("managed-only", environmentDescriptor.sqlite().libraryMode());
    assertEquals(
        "self-contained-bundle", environmentDescriptor.distribution().publicCliDistribution());
    assertEquals(
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        environmentDescriptor.distribution().supportedPublicCliBundleTargets());
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
        environmentDescriptor.distribution().unsupportedPublicCliOperatingSystems());
    assertEquals(
        ProtocolCatalog.sourceCheckoutJava(),
        environmentDescriptor.distribution().sourceCheckoutJava());
    assertEquals(
        "FINGRIND_SQLITE_LIBRARY", environmentDescriptor.sqlite().libraryEnvironmentVariable());
    assertEquals("fingrind.bundle.home", environmentDescriptor.sqlite().bundleHomeSystemProperty());
    assertEquals(
        List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
        environmentDescriptor.sqlite().requiredCompileOptions());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        environmentDescriptor.sqlite().compileOptionsVerification());
    assertEquals("3.53.0", environmentDescriptor.sqlite().requiredMinimumSqliteVersion());
    assertEquals("2.3.3", environmentDescriptor.sqlite().requiredSqlite3mcVersion());
    assertEquals("unavailable", environmentDescriptor.sqlite().runtimeStatus());
    assertEquals("managed sqlite unavailable", environmentDescriptor.sqlite().runtimeIssue());
    assertNull(environmentDescriptor.sqlite().loadedSqliteVersion());
    assertNull(environmentDescriptor.sqlite().loadedSqlite3mcVersion());
  }

  @Test
  void run_generatesBookKeyFileWithNonSecretMetadata() throws IOException {
    Path keyFilePath = tempDirectory.resolve("secrets").resolve("entity.book-key");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(new String[] {"generate-book-key-file", "--book-key-file", keyFilePath.toString()});

    assertEquals(0, exitCode);
    assertTrue(Files.isRegularFile(keyFilePath));
    JsonNode payload = new ObjectMapper().readTree(outputStream.toByteArray()).path("payload");
    assertGeneratedKeyFileIsSecure(keyFilePath, payload.path("permissions").asString());
    assertEquals(
        keyFilePath.toAbsolutePath().normalize().toString(),
        payload.path("bookKeyFile").asString());
    assertEquals("base64url-no-padding", payload.path("encoding").asString());
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
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(new String[] {"generate-book-key-file", "--book-key-file", keyFilePath.toString()});

    assertEquals(2, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.BOOK_KEY_FILE_ALREADY_EXISTS.code(),
        failureEnvelope.path("code").asString());
    assertTrue(failureEnvelope.path("message").asString().contains("already exists"));
  }

  @Test
  void run_printsRequestTemplateWithoutCallerSuppliedCommitFields() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"print-request-template"});

    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"effectiveDate\""));
    assertTrue(json.contains("\"provenance\""));
    assertFalse(json.contains("recordedAt"));
    assertFalse(json.contains("sourceChannel"));
  }

  @Test
  void run_printsPlanTemplateForAgentWorkflows() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"print-plan-template"});

    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"planId\""));
    assertFalse(json.contains("\"executionPolicy\""));
    assertTrue(json.contains("\"initialize-book\""));
    assertTrue(json.contains("\"assert-account-balance\""));
    assertTrue(json.contains("\"assertion\""));
    assertFalse(json.contains("\"accountBalanceAssertion\""));
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
        new FinGrindCli(
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
