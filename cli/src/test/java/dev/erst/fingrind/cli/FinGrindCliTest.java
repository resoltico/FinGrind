package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookMigrationPolicy;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerExecutionJournal;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.sqlite.ManagedSqliteRuntimeUnavailableException;
import dev.erst.fingrind.sqlite.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import dev.erst.fingrind.sqlite.SqliteStorageFailureException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliTest {
  private static final String TEST_BOOK_KEY = "cli-test-book-key";

  @TempDir Path tempDirectory;

  @Test
  void run_returnsHelpWhenArgumentsAreEmpty() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[0]);

    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"open-book\""));
    assertTrue(json.contains("\"declare-account\""));
    assertTrue(json.contains("\"list-accounts\""));
  }

  @Test
  void run_returnsCapabilities() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"capabilities"});

    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    JsonNode payload = new ObjectMapper().readTree(json).path("payload");
    assertTrue(json.contains("\"administrationCommands\""));
    assertTrue(json.contains("\"queryCommands\""));
    assertTrue(json.contains("\"administration-book-not-initialized\""));
    assertTrue(json.contains("\"query-book-not-initialized\""));
    assertTrue(json.contains("\"posting-book-not-initialized\""));
    assertTrue(json.contains("\"account-normal-balance-conflict\""));
    assertTrue(json.contains("\"posting-not-found\""));
    assertEquals(
        "[\"generate-book-key-file\",\"open-book\",\"rekey-book\",\"declare-account\"]",
        payload.path("administrationCommands").toString());
    assertEquals(
        "[\"inspect-book\",\"list-accounts\",\"get-posting\",\"list-postings\",\"account-balance\"]",
        payload.path("queryCommands").toString());
    assertTrue(payload.path("requestShapes").has("postEntry"));
    assertTrue(payload.path("requestShapes").has("declareAccount"));
    assertEquals("advisory", payload.path("preflightSemantics").asString());
    assertFalse(payload.path("preflight").path("isCommitGuarantee").asBoolean(true));
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
        "sqlite-ffm-sqlite3mc", payload.path("environment").path("storageDriver").asString());
    assertEquals("required", payload.path("environment").path("bookProtectionMode").asString());
    assertEquals("chacha20", payload.path("environment").path("defaultBookCipher").asString());
    assertEquals("managed-only", payload.path("environment").path("sqliteLibraryMode").asString());
    assertEquals(
        "self-contained-bundle",
        payload.path("environment").path("publicCliDistribution").asString());
    assertEquals(
        "direct-java-invocation",
        payload.path("environment").path("runtimeDistribution").asString());
    assertEquals(
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        readTextArray(payload.path("environment").path("supportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
        readTextArray(payload.path("environment").path("unsupportedPublicCliOperatingSystems")));
    assertEquals(
        "FINGRIND_SQLITE_LIBRARY",
        payload.path("environment").path("sqliteLibraryEnvironmentVariable").asString());
    assertEquals(
        "fingrind.bundle.home",
        payload.path("environment").path("sqliteLibraryBundleHomeSystemProperty").asString());
    assertEquals(
        "[\"THREADSAFE=1\",\"OMIT_LOAD_EXTENSION\",\"TEMP_STORE=3\",\"SECURE_DELETE\"]",
        payload.path("environment").path("requiredSqliteCompileOptions").toString());
    assertTrue(payload.path("environment").path("sqliteCompileOptionsVerified").asBoolean());
    assertEquals("2.3.3", payload.path("environment").path("requiredSqlite3mcVersion").asString());
    assertEquals("2.3.3", payload.path("environment").path("loadedSqlite3mcVersion").asString());
    assertEquals(
        "[\"--book-key-file\",\"--book-passphrase-stdin\",\"--book-passphrase-prompt\"]",
        payload.path("requestInput").path("bookPassphraseOptions").toString());
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

    int exitCode = cli.run(new String[] {"version"});

    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"application\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"version\""));
  }

  @Test
  void environmentDescriptor_reportsUnavailableRuntimeWhenSqliteProbeFails() {
    ContractDiscovery.EnvironmentDescriptor environmentDescriptor =
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

    assertEquals("source-checkout-gradle", environmentDescriptor.runtimeDistribution());
    assertEquals("sqlite-ffm-sqlite3mc", environmentDescriptor.storageDriver());
    assertEquals("sqlite", environmentDescriptor.storageEngine());
    assertEquals("required", environmentDescriptor.bookProtectionMode());
    assertEquals("chacha20", environmentDescriptor.defaultBookCipher());
    assertEquals("managed-only", environmentDescriptor.sqliteLibraryMode());
    assertEquals("self-contained-bundle", environmentDescriptor.publicCliDistribution());
    assertEquals(
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        environmentDescriptor.supportedPublicCliBundleTargets());
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
        environmentDescriptor.unsupportedPublicCliOperatingSystems());
    assertEquals("26+", environmentDescriptor.sourceCheckoutJava());
    assertEquals(
        "FINGRIND_SQLITE_LIBRARY", environmentDescriptor.sqliteLibraryEnvironmentVariable());
    assertEquals(
        "fingrind.bundle.home", environmentDescriptor.sqliteLibraryBundleHomeSystemProperty());
    assertEquals(
        List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
        environmentDescriptor.requiredSqliteCompileOptions());
    assertFalse(environmentDescriptor.sqliteCompileOptionsVerified());
    assertEquals("3.53.0", environmentDescriptor.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", environmentDescriptor.requiredSqlite3mcVersion());
    assertEquals("unavailable", environmentDescriptor.sqliteRuntimeStatus());
    assertEquals("managed sqlite unavailable", environmentDescriptor.sqliteRuntimeIssue());
    assertNull(environmentDescriptor.loadedSqliteVersion());
    assertNull(environmentDescriptor.loadedSqlite3mcVersion());
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
  void run_executesOpenBookPlanThroughDefaultSqliteWorkflow() throws IOException {
    Path planFile = writeNamedRequest("open-plan.json", openOnlyPlanJson());
    Path bookFilePath = tempDirectory.resolve("plans").resolve("new-book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              planFile.toString()
            });

    assertEquals(0, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"plan-committed\""));
    assertTrue(Files.exists(bookFilePath));
  }

  @Test
  void run_executesNonOpeningPlanAgainstExistingBookThroughDefaultSqliteWorkflow()
      throws IOException {
    Path planFile = writeNamedRequest("declare-plan.json", validPlanJson());
    Path bookFilePath = tempDirectory.resolve("plans").resolve("existing-book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    FinGrindCli openCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(new ByteArrayOutputStream()),
            fixedClock());

    assertEquals(
        0,
        openCli.run(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            }));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli executeCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        executeCli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              planFile.toString()
            });

    assertEquals(0, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"plan-committed\""));
  }

  @Test
  void run_rejectsPlanWithoutOpenBookAgainstMissingBookThroughDefaultSqliteWorkflow()
      throws IOException {
    Path planFile = writeNamedRequest("declare-plan.json", validPlanJson());
    Path bookFilePath = tempDirectory.resolve("plans").resolve("missing-book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              planFile.toString()
            });

    assertEquals(2, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"plan-rejected\""));
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"administration-book-not-initialized\""));
  }

  @Test
  void cliFailure_rejectsUnsupportedExceptionTypes() throws Exception {
    MethodHandle cliFailure =
        MethodHandles.privateLookupIn(FinGrindCli.class, MethodHandles.lookup())
            .findStatic(
                FinGrindCli.class,
                "cliFailure",
                MethodType.methodType(CliFailure.class, Exception.class));

    IllegalArgumentException thrown =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> cliFailure.invokeWithArguments(new IllegalStateException("boom")));

    assertEquals("Unsupported CLI failure type.", thrown.getMessage());
  }

  @Test
  void run_rejectsPreflightAgainstUninitializedBookThroughDefaultSqliteWorkflow()
      throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("live-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(2, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"posting-book-not-initialized\""));
    assertFalse(Files.exists(bookFilePath));
  }

  @Test
  void run_openBookAndListAccountsThroughDefaultSqliteWorkflowSupportsStandardInputPassphrase()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("stdin-books").resolve("entity.sqlite");

    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        new FinGrindCli(
            new ByteArrayInputStream((TEST_BOOK_KEY + "\n").getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(openOutput),
            fixedClock());

    assertEquals(
        0,
        openCli.run(
            new String[] {
              "open-book", "--book-file", bookFilePath.toString(), "--book-passphrase-stdin"
            }));
    assertTrue(openOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));

    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    FinGrindCli listCli =
        new FinGrindCli(
            new ByteArrayInputStream((TEST_BOOK_KEY + "\n").getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(listOutput),
            fixedClock());

    assertEquals(
        0,
        listCli.run(
            new String[] {
              "list-accounts", "--book-file", bookFilePath.toString(), "--book-passphrase-stdin"
            }));
    assertTrue(listOutput.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
  }

  @Test
  void run_openBookThroughDefaultSqliteWorkflowRejectsPromptPassphraseWithoutInteractiveConsole() {
    Path bookFilePath = tempDirectory.resolve("no-console-books").resolve("entity.sqlite");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              "open-book", "--book-file", bookFilePath.toString(), "--book-passphrase-prompt"
            });

    assertEquals(1, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains(
                "FinGrind cannot prompt for a book passphrase because no interactive console is available."));
  }

  @Test
  void run_openBookAndListAccountsThroughDefaultSqliteWorkflowSupportsInteractivePromptPassphrase()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("prompt-books").resolve("entity.sqlite");
    CliBookPassphraseResolver.Terminal terminal = prompt -> TEST_BOOK_KEY.toCharArray();

    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(openOutput),
            fixedClock(),
            terminal);

    assertEquals(
        0,
        openCli.run(
            new String[] {
              "open-book", "--book-file", bookFilePath.toString(), "--book-passphrase-prompt"
            }));
    assertTrue(openOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));

    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    FinGrindCli listCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(listOutput),
            fixedClock(),
            terminal);

    assertEquals(
        0,
        listCli.run(
            new String[] {
              "list-accounts", "--book-file", bookFilePath.toString(), "--book-passphrase-prompt"
            }));
    assertTrue(listOutput.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
  }

  @Test
  void run_rekeyBookThroughDefaultSqliteWorkflowRotatesBookKey() throws IOException {
    Path bookFilePath = tempDirectory.resolve("rekey-books").resolve("entity.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath, TEST_BOOK_KEY);
    Path replacementBookKeyFilePath = writeNamedBookKey("replacement-book.key", "replacement-key");

    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        openCli.run(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              currentBookKeyFilePath.toString()
            }));

    ByteArrayOutputStream rekeyOutput = new ByteArrayOutputStream();
    FinGrindCli rekeyCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(rekeyOutput), fixedClock());
    assertEquals(
        0,
        rekeyCli.run(
            new String[] {
              "rekey-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              currentBookKeyFilePath.toString(),
              "--new-book-key-file",
              replacementBookKeyFilePath.toString()
            }));
    assertTrue(rekeyOutput.toString(StandardCharsets.UTF_8).contains("\"bookFile\""));

    ByteArrayOutputStream oldKeyOutput = new ByteArrayOutputStream();
    FinGrindCli oldKeyCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(oldKeyOutput), fixedClock());
    assertEquals(
        1,
        oldKeyCli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              currentBookKeyFilePath.toString()
            }));
    assertTrue(
        oldKeyOutput.toString(StandardCharsets.UTF_8).contains("\"code\":\"runtime-failure\""));

    ByteArrayOutputStream newKeyOutput = new ByteArrayOutputStream();
    FinGrindCli newKeyCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(newKeyOutput), fixedClock());
    assertEquals(
        0,
        newKeyCli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              replacementBookKeyFilePath.toString()
            }));
    assertTrue(newKeyOutput.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
  }

  @Test
  void run_openBookDeclareAccountListAccountsAndCommitThroughDefaultSqliteWorkflow()
      throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest("declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("committed-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    FinGrindCli cli;

    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            }));
    assertTrue(openOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));

    ByteArrayOutputStream declareCashOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(declareCashOutput),
            fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              declareCashFile.toString()
            }));
    assertTrue(
        declareCashOutput.toString(StandardCharsets.UTF_8).contains("\"accountCode\":\"1000\""));

    ByteArrayOutputStream declareRevenueOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(declareRevenueOutput),
            fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              declareRevenueFile.toString()
            }));

    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(listOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            }));
    assertTrue(listOutput.toString(StandardCharsets.UTF_8).contains("\"accountName\":\"Cash\""));
    assertTrue(listOutput.toString(StandardCharsets.UTF_8).contains("\"accountName\":\"Revenue\""));

    ByteArrayOutputStream preflightOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(preflightOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            }));
    assertTrue(
        preflightOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"status\":\"preflight-accepted\""));

    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            new String[] {
              "post-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            }));

    JsonNode envelope = new ObjectMapper().readTree(commitOutput.toString(StandardCharsets.UTF_8));
    assertEquals("committed", envelope.path("status").asString());
    UUID postingId = UUID.fromString(envelope.path("postingId").asString());
    assertEquals(7, postingId.version());
    assertEquals(2, postingId.variant());
    assertTrue(Files.exists(bookFilePath));
  }

  @Test
  void run_queryCommandsThroughDefaultSqliteWorkflow() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest("query-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest(
            "query-declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("query-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);

    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "open-book",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
                }));
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareCashFile.toString()
                }));
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareRevenueFile.toString()
                }));

    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock())
            .run(
                new String[] {
                  "post-entry",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  requestFile.toString()
                }));
    String postingId =
        new ObjectMapper()
            .readTree(commitOutput.toString(StandardCharsets.UTF_8))
            .path("postingId")
            .asText();

    ByteArrayOutputStream inspectOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]), utf8PrintStream(inspectOutput), fixedClock())
            .run(
                new String[] {
                  "inspect-book",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
                }));
    assertTrue(
        inspectOutput.toString(StandardCharsets.UTF_8).contains("\"state\":\"initialized\""));

    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(getPostingOutput),
                fixedClock())
            .run(
                new String[] {
                  "get-posting",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--posting-id",
                  postingId
                }));
    assertTrue(getPostingOutput.toString(StandardCharsets.UTF_8).contains(postingId));

    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(listPostingsOutput),
                fixedClock())
            .run(
                new String[] {
                  "list-postings",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--limit",
                  "10"
                }));
    assertTrue(listPostingsOutput.toString(StandardCharsets.UTF_8).contains(postingId));

    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]), utf8PrintStream(balanceOutput), fixedClock())
            .run(
                new String[] {
                  "account-balance",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--account-code",
                  "1000"
                }));
    assertTrue(balanceOutput.toString(StandardCharsets.UTF_8).contains("\"accountCode\":\"1000\""));
    assertTrue(balanceOutput.toString(StandardCharsets.UTF_8).contains("\"balances\""));
  }

  @Test
  void run_routesCommandsThroughSelectedBookWorkflow() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path planFile = writeNamedRequest("plan.json", validPlanJson());
    Path declareAccountFile =
        writeNamedRequest("declare.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
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
            new ListAccountsResult.Listed(
                new AccountPage(
                    List.of(
                        new DeclaredAccount(
                            new AccountCode("1000"),
                            new AccountName("Cash"),
                            NormalBalance.DEBIT,
                            true,
                            Instant.parse("2026-04-07T12:00:00Z"))),
                    25,
                    10,
                    false)),
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

    assertEquals(
        0,
        cli.run(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            }));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              declareAccountFile.toString()
            }));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--limit",
              "25",
              "--offset",
              "10"
            }));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            }));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "post-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            }));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              planFile.toString()
            }));

    assertEquals(List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.openBookAccesses());
    assertEquals(
        List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.declareAccountAccesses());
    assertEquals(
        List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.listAccountAccesses());
    assertEquals(List.of(new ListAccountsQuery(25, 10)), workflow.listAccountQueries());
    assertEquals(List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.preflightAccesses());
    assertEquals(List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.commitAccesses());
    assertEquals(
        List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.executePlanAccesses());
  }

  @Test
  void run_mapsAssertionFailedPlansToExitCodeThree() throws IOException {
    Path planFile = writeNamedRequest("assertion-plan.json", validPlanJson());
    Path bookFilePath = tempDirectory.resolve("books").resolve("assertion.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
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
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, 0, false)),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    workflow.setExecutePlanResult(assertionFailedPlanResult("plan-1"));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    int exitCode =
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(outputStream),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "execute-plan",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  planFile.toString()
                });

    assertEquals(3, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"status\":\"plan-assertion-failed\""));
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"assertion-failed\""));
  }

  @Test
  void run_routesRekeyBookThroughSelectedBookWorkflow() {
    Path bookFilePath = tempDirectory.resolve("books").resolve("rekey.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath);
    Path replacementBookKeyFilePath = writeNamedBookKey("replacement.key", "replacement-key");
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(bookFilePath),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, 0, false)),
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

    assertEquals(
        0,
        cli.run(
            new String[] {
              "rekey-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              currentBookKeyFilePath.toString(),
              "--new-book-key-file",
              replacementBookKeyFilePath.toString()
            }));

    assertEquals(
        List.of(bookAccess(bookFilePath, currentBookKeyFilePath)), workflow.rekeyBookAccesses());
    assertEquals(
        List.of(new BookAccess.PassphraseSource.KeyFile(replacementBookKeyFilePath)),
        workflow.rekeyReplacementPassphraseSources());
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
  }

  @Test
  void run_mapsBookWorkflowRejectionsToExitCodeTwo() throws IOException {
    Path declareAccountFile =
        writeNamedRequest("declare.json", declareAccountJson("1000", "Cash", "DEBIT"));
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
            new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
            new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
            new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new PostEntryResult.PreflightRejected(
                new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()),
            new PostEntryResult.CommitRejected(
                new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()));
    Path bookFilePath = tempDirectory.resolve("reject.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path requestFile = writeRequest(validRequestJson());

    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "open-book",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
                }));
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "rekey-book",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--new-book-key-file",
                  tempDirectory.resolve("replacement.key").toString()
                }));
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  declareAccountFile.toString()
                }));
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "list-accounts",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
                }));
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "preflight-entry",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  requestFile.toString()
                }));
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "post-entry",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  requestFile.toString()
                }));
  }

  @Test
  void run_mapsQueryWorkflowRejectionsToExitCodeTwo() throws IOException {
    Path bookFilePath = tempDirectory.resolve("query-reject.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    FinGrindCli.BookWorkflow workflow =
        new FinGrindCli.BookWorkflow() {
          @Override
          public OpenBookResult openBook(BookAccess bookAccess) {
            throw new AssertionError("openBook should not be called in this test");
          }

          @Override
          public RekeyBookResult rekeyBook(
              BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
            throw new AssertionError("rekeyBook should not be called in this test");
          }

          @Override
          public DeclareAccountResult declareAccount(
              BookAccess bookAccess, DeclareAccountCommand command) {
            throw new AssertionError("declareAccount should not be called in this test");
          }

          @Override
          public BookInspection inspectBook(BookAccess bookAccess) {
            return new BookInspection.Initialized(
                1_179_079_236,
                1,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
                Instant.parse("2026-04-07T10:15:30Z"));
          }

          @Override
          public ListAccountsResult listAccounts(BookAccess bookAccess, ListAccountsQuery query) {
            throw new AssertionError("listAccounts should not be called in this test");
          }

          @Override
          public GetPostingResult getPosting(BookAccess bookAccess, PostingId postingId) {
            return new GetPostingResult.Rejected(new BookQueryRejection.PostingNotFound(postingId));
          }

          @Override
          public ListPostingsResult listPostings(BookAccess bookAccess, ListPostingsQuery query) {
            return new ListPostingsResult.Rejected(
                new BookQueryRejection.UnknownAccount(new AccountCode("9999")));
          }

          @Override
          public AccountBalanceResult accountBalance(
              BookAccess bookAccess, AccountBalanceQuery query) {
            return new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized());
          }

          @Override
          public LedgerPlanResult executePlan(BookAccess bookAccess, LedgerPlan plan) {
            throw new AssertionError("executePlan should not be called in this test");
          }

          @Override
          public PreflightEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
            throw new AssertionError("preflight should not be called in this test");
          }

          @Override
          public CommitEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
            throw new AssertionError("commit should not be called in this test");
          }
        };

    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(getPostingOutput),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "get-posting",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--posting-id",
                  "posting-missing"
                }));
    assertTrue(
        getPostingOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"posting-not-found\""));

    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(listPostingsOutput),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "list-postings",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString()
                }));
    assertTrue(
        listPostingsOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"unknown-account\""));

    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    assertEquals(
        2,
        new FinGrindCli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(balanceOutput),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "account-balance",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--account-code",
                  "1000"
                }));
    assertTrue(
        balanceOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"query-book-not-initialized\""));
  }

  @Test
  void run_rejectsMissingBookFile() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"open-book"});

    assertEquals(2, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"argument\":\"--book-file\""));
  }

  @Test
  void run_mapsSqliteRuntimeFailureToRuntimeFailureWithSqliteHint() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new SqliteStorageFailureException("Failed to open SQLite book connection.")));

    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(1, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"runtime-failure\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("initialization state"));
  }

  @Test
  void run_mapsManagedSqliteRuntimeFailureToRuntimeFailureWithEnvironmentHint() throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "FINGRIND_SQLITE_LIBRARY is not configured.")));

    int exitCode =
        cli.run(
            new String[] {
              "rekey-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--new-book-key-file",
              tempDirectory.resolve("replacement.key").toString()
            });

    assertEquals(1, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("runtime-failure", failureEnvelope.path("code").asString());
    assertTrue(
        failureEnvelope
            .path("hint")
            .asString()
            .contains(
                "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.cmd on Windows)"));
  }

  @Test
  void run_mapsBundleHomeRuntimeFailureToRuntimeFailureWithEnvironmentHint() throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "fingrind.bundle.home did not resolve a bundled SQLite library.")));

    int exitCode =
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            });

    assertEquals(1, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("runtime-failure", failureEnvelope.path("code").asString());
    assertTrue(
        failureEnvelope
            .path("hint")
            .asString()
            .contains(
                "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.cmd on Windows)"));
  }

  @Test
  void run_mapsBundleLauncherRuntimeFailureToRuntimeFailureWithEnvironmentHint()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "bin/fingrind must be used from the extracted bundle root.")));

    int exitCode =
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            });

    assertEquals(1, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("runtime-failure", failureEnvelope.path("code").asString());
    assertTrue(
        failureEnvelope
            .path("hint")
            .asString()
            .contains(
                "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.cmd on Windows)"));
  }

  @Test
  void run_mapsWindowsBundleLauncherRuntimeFailureToRuntimeFailureWithEnvironmentHint()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "bin\\fingrind.cmd must be used from the extracted bundle root.")));

    int exitCode =
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            });

    assertEquals(1, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("runtime-failure", failureEnvelope.path("code").asString());
    assertTrue(
        failureEnvelope
            .path("hint")
            .asString()
            .contains(
                "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.cmd on Windows)"));
  }

  @Test
  void run_mapsGenericRuntimeFailureToRuntimeFailureWithGenericHint() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(new IllegalStateException("boom")));

    int exitCode =
        cli.run(
            new String[] {
              "post-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(1, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"runtime-failure\""));
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains(
                "Inspect the message and rerun after fixing the underlying runtime problem."));
  }

  @Test
  void run_mapsGenericIllegalArgumentExceptionToRuntimeFailure() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new IllegalArgumentWorkflow());

    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(1, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"runtime-failure\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("workflow boom"));
  }

  @Test
  void run_mapsCliRequestExceptionToInvalidRequestWithoutInvokingWorkflow() throws IOException {
    Path requestFile = writeNamedRequest("broken-declare-account.json", "{");
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
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
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, 0, false)),
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

    int exitCode =
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(2, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"invalid-request\""));
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("Failed to read request JSON."));
    assertFalse(workflow.workflowInvoked());
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
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, 0, false)),
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

    int exitCode = cli.run(new String[] {"capabilities"});

    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\""));
    assertFalse(workflow.workflowInvoked());
  }

  private Path writeRequest(String payload) throws IOException {
    return writeNamedRequest("request.json", payload);
  }

  private Path writeNamedRequest(String fileName, String payload) throws IOException {
    Path requestFile = tempDirectory.resolve(fileName);
    Files.writeString(requestFile, payload, StandardCharsets.UTF_8);
    return requestFile;
  }

  private Path writeBookKey(Path bookFilePath) {
    return writeBookKey(bookFilePath, TEST_BOOK_KEY);
  }

  private Path writeBookKey(Path bookFilePath, String keyText) {
    try {
      Path bookKeyFilePath = bookFilePath.resolveSibling(bookFilePath.getFileName() + ".key");
      if (bookKeyFilePath.getParent() != null) {
        Files.createDirectories(bookKeyFilePath.getParent());
      }
      writeSecureKey(bookKeyFilePath, keyText);
      return bookKeyFilePath;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private Path writeNamedBookKey(String fileName, String keyText) {
    try {
      Path keyFilePath = tempDirectory.resolve(fileName);
      writeSecureKey(keyFilePath, keyText);
      return keyFilePath;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static void writeSecureKey(Path keyFilePath, String keyText) throws IOException {
    if (keyFilePath.getParent() != null) {
      Files.createDirectories(keyFilePath.getParent());
    }
    if (Files.notExists(keyFilePath)) {
      SqliteBookKeyFileGenerator.generate(keyFilePath);
    }
    Files.writeString(keyFilePath, keyText, StandardCharsets.UTF_8);
  }

  private static void assertGeneratedKeyFileIsSecure(Path keyFilePath, String permissions)
      throws IOException {
    try (SqliteBookPassphrase ignored = SqliteBookKeyFile.load(keyFilePath)) {
      if (supportsPosix(keyFilePath)) {
        assertEquals("0600", permissions);
        assertEquals(
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(keyFilePath));
        return;
      }
      assertEquals("owner-only-acl", permissions);
      assertOwnerOnlyAcl(keyFilePath);
    }
  }

  private static void assertOwnerOnlyAcl(Path keyFilePath) throws IOException {
    AclFileAttributeView view = Files.getFileAttributeView(keyFilePath, AclFileAttributeView.class);
    UserPrincipal owner = view.getOwner();
    assertTrue(
        view.getAcl().stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> owner.equals(entry.principal()))
            .anyMatch(entry -> entry.permissions().contains(AclEntryPermission.READ_DATA)));
    assertFalse(
        view.getAcl().stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> !owner.equals(entry.principal()))
            .map(AclEntry::permissions)
            .anyMatch(permissions -> permissions.contains(AclEntryPermission.READ_DATA)));
  }

  private static boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains("posix");
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);
  }

  private static List<String> readTextArray(JsonNode node) {
    List<String> values = new java.util.ArrayList<>();
    node.forEach(element -> values.add(element.asText()));
    return List.copyOf(values);
  }

  private static String validRequestJson() {
    return """
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
                "causationId": "cause-1"
              }
            }
            """;
  }

  private static String validPlanJson() {
    return """
            {
              "planId": "plan-1",
              "steps": [
                {
                  "stepId": "declare-cash",
                  "kind": "declare-account",
                  "declareAccount": {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "normalBalance": "DEBIT"
                  }
                }
              ]
            }
            """;
  }

  private static String openOnlyPlanJson() {
    return """
            {
              "planId": "plan-1",
              "steps": [
                {
                  "stepId": "open",
                  "kind": "open-book"
                }
              ]
            }
            """;
  }

  private static String declareAccountJson(
      String accountCode, String accountName, String normalBalance) {
    return """
            {
              "accountCode": "%s",
              "accountName": "%s",
              "normalBalance": "%s"
            }
            """
        .formatted(accountCode, accountName, normalBalance);
  }

  private static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }

  /** Recording workflow used to assert CLI routing without opening SQLite. */
  private static final class RecordingWorkflow implements FinGrindCli.BookWorkflow {
    private final List<BookAccess> openBookAccesses = new ArrayList<>();
    private final List<BookAccess> rekeyBookAccesses = new ArrayList<>();
    private final List<BookAccess.PassphraseSource> rekeyReplacementPassphraseSources =
        new ArrayList<>();
    private final List<BookAccess> declareAccountAccesses = new ArrayList<>();
    private final List<BookAccess> listAccountAccesses = new ArrayList<>();
    private final List<ListAccountsQuery> listAccountQueries = new ArrayList<>();
    private final List<BookAccess> executePlanAccesses = new ArrayList<>();
    private final List<BookAccess> preflightAccesses = new ArrayList<>();
    private final List<BookAccess> commitAccesses = new ArrayList<>();
    private final OpenBookResult openBookResult;
    private final RekeyBookResult rekeyBookResult;
    private final DeclareAccountResult declareAccountResult;
    private final ListAccountsResult listAccountsResult;
    private final PreflightEntryResult preflightResult;
    private final CommitEntryResult commitResult;
    private LedgerPlanResult executePlanResult;

    private RecordingWorkflow(
        OpenBookResult openBookResult,
        RekeyBookResult rekeyBookResult,
        DeclareAccountResult declareAccountResult,
        ListAccountsResult listAccountsResult,
        PreflightEntryResult preflightResult,
        CommitEntryResult commitResult) {
      this.openBookResult = openBookResult;
      this.rekeyBookResult = rekeyBookResult;
      this.declareAccountResult = declareAccountResult;
      this.listAccountsResult = listAccountsResult;
      this.preflightResult = preflightResult;
      this.commitResult = commitResult;
    }

    @Override
    public OpenBookResult openBook(BookAccess bookAccess) {
      openBookAccesses.add(bookAccess);
      return openBookResult;
    }

    @Override
    public RekeyBookResult rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      rekeyBookAccesses.add(bookAccess);
      rekeyReplacementPassphraseSources.add(replacementPassphraseSource);
      return rekeyBookResult;
    }

    @Override
    public DeclareAccountResult declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      declareAccountAccesses.add(bookAccess);
      return declareAccountResult;
    }

    @Override
    public ListAccountsResult listAccounts(BookAccess bookAccess, ListAccountsQuery query) {
      listAccountAccesses.add(bookAccess);
      listAccountQueries.add(query);
      return listAccountsResult;
    }

    @Override
    public BookInspection inspectBook(BookAccess bookAccess) {
      throw new AssertionError("inspectBook should not be called in this test");
    }

    @Override
    public GetPostingResult getPosting(BookAccess bookAccess, PostingId postingId) {
      throw new AssertionError("getPosting should not be called in this test");
    }

    @Override
    public ListPostingsResult listPostings(BookAccess bookAccess, ListPostingsQuery query) {
      throw new AssertionError("listPostings should not be called in this test");
    }

    @Override
    public AccountBalanceResult accountBalance(BookAccess bookAccess, AccountBalanceQuery query) {
      throw new AssertionError("accountBalance should not be called in this test");
    }

    @Override
    public LedgerPlanResult executePlan(BookAccess bookAccess, LedgerPlan plan) {
      executePlanAccesses.add(bookAccess);
      return executePlanResult == null ? successfulPlanResult(plan.planId()) : executePlanResult;
    }

    @Override
    public PreflightEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
      preflightAccesses.add(bookAccess);
      return preflightResult;
    }

    @Override
    public CommitEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
      commitAccesses.add(bookAccess);
      return commitResult;
    }

    private List<BookAccess> openBookAccesses() {
      return openBookAccesses;
    }

    private List<BookAccess> declareAccountAccesses() {
      return declareAccountAccesses;
    }

    private List<BookAccess> rekeyBookAccesses() {
      return rekeyBookAccesses;
    }

    private List<BookAccess.PassphraseSource> rekeyReplacementPassphraseSources() {
      return rekeyReplacementPassphraseSources;
    }

    private List<BookAccess> listAccountAccesses() {
      return listAccountAccesses;
    }

    private List<ListAccountsQuery> listAccountQueries() {
      return listAccountQueries;
    }

    private List<BookAccess> executePlanAccesses() {
      return executePlanAccesses;
    }

    private List<BookAccess> preflightAccesses() {
      return preflightAccesses;
    }

    private List<BookAccess> commitAccesses() {
      return commitAccesses;
    }

    private boolean workflowInvoked() {
      return !openBookAccesses.isEmpty()
          || !rekeyBookAccesses.isEmpty()
          || !declareAccountAccesses.isEmpty()
          || !listAccountAccesses.isEmpty()
          || !executePlanAccesses.isEmpty()
          || !preflightAccesses.isEmpty()
          || !commitAccesses.isEmpty();
    }

    private void setExecutePlanResult(LedgerPlanResult executePlanResult) {
      this.executePlanResult = executePlanResult;
    }
  }

  /** Workflow stub that always throws the same runtime failure. */
  private static final class ExplodingWorkflow implements FinGrindCli.BookWorkflow {
    private final RuntimeException failure;

    private ExplodingWorkflow(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public OpenBookResult openBook(BookAccess bookAccess) {
      throw failure;
    }

    @Override
    public RekeyBookResult rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      throw failure;
    }

    @Override
    public DeclareAccountResult declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      throw failure;
    }

    @Override
    public ListAccountsResult listAccounts(BookAccess bookAccess, ListAccountsQuery query) {
      throw failure;
    }

    @Override
    public BookInspection inspectBook(BookAccess bookAccess) {
      throw failure;
    }

    @Override
    public GetPostingResult getPosting(BookAccess bookAccess, PostingId postingId) {
      throw failure;
    }

    @Override
    public ListPostingsResult listPostings(BookAccess bookAccess, ListPostingsQuery query) {
      throw failure;
    }

    @Override
    public AccountBalanceResult accountBalance(BookAccess bookAccess, AccountBalanceQuery query) {
      throw failure;
    }

    @Override
    public LedgerPlanResult executePlan(BookAccess bookAccess, LedgerPlan plan) {
      throw failure;
    }

    @Override
    public PreflightEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
      throw failure;
    }

    @Override
    public CommitEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
      throw failure;
    }
  }

  /** Workflow stub that always throws an invalid-request style exception. */
  private static final class IllegalArgumentWorkflow implements FinGrindCli.BookWorkflow {
    @Override
    public OpenBookResult openBook(BookAccess bookAccess) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public RekeyBookResult rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public DeclareAccountResult declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ListAccountsResult listAccounts(BookAccess bookAccess, ListAccountsQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public BookInspection inspectBook(BookAccess bookAccess) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public GetPostingResult getPosting(BookAccess bookAccess, PostingId postingId) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public ListPostingsResult listPostings(BookAccess bookAccess, ListPostingsQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public AccountBalanceResult accountBalance(BookAccess bookAccess, AccountBalanceQuery query) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public LedgerPlanResult executePlan(BookAccess bookAccess, LedgerPlan plan) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public PreflightEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public CommitEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }
  }

  private static BookAccess bookAccess(Path bookFilePath, Path bookKeyFilePath) {
    return new BookAccess(bookFilePath, new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath));
  }

  private static LedgerPlanResult successfulPlanResult(String planId) {
    Instant timestamp = fixedClock().instant();
    return new LedgerPlanResult.Succeeded(
        planId,
        new LedgerExecutionJournal(
            planId,
            LedgerPlanStatus.SUCCEEDED,
            timestamp,
            timestamp,
            List.of(
                new LedgerJournalEntry.Succeeded(
                    "inspect",
                    LedgerStepKind.INSPECT_BOOK,
                    Optional.empty(),
                    timestamp,
                    timestamp,
                    List.of(LedgerFact.flag("ok", true), LedgerFact.count("count", 1))))));
  }

  private static LedgerPlanResult assertionFailedPlanResult(String planId) {
    Instant timestamp = fixedClock().instant();
    LedgerStepFailure failure =
        new LedgerStepFailure("assertion-failed", "Assertion failed.", List.of());
    return new LedgerPlanResult.AssertionFailed(
        planId,
        new LedgerExecutionJournal(
            planId,
            LedgerPlanStatus.ASSERTION_FAILED,
            timestamp,
            timestamp,
            List.of(
                new LedgerJournalEntry.AssertionFailed(
                    "assert",
                    LedgerStepKind.ASSERT,
                    Optional.of(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS),
                    timestamp,
                    timestamp,
                    List.of(),
                    failure))));
  }
}
