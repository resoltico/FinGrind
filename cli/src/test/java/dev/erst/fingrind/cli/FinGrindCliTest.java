package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.application.BookAccess;
import dev.erst.fingrind.application.BookAdministrationRejection;
import dev.erst.fingrind.application.DeclareAccountCommand;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.ListAccountsResult;
import dev.erst.fingrind.application.MachineContract;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.sqlite.RekeyBookResult;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
    assertTrue(json.contains("\"book-not-initialized\""));
    assertTrue(json.contains("\"account-normal-balance-conflict\""));
    assertEquals(
        "[\"generate-book-key-file\",\"open-book\",\"rekey-book\",\"declare-account\"]",
        payload.path("administrationCommands").toString());
    assertEquals("[\"list-accounts\"]", payload.path("queryCommands").toString());
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
    MachineContract.EnvironmentDescriptor environmentDescriptor =
        FinGrindCli.environmentDescriptor(
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                "managed sqlite unavailable"));

    assertEquals("sqlite-ffm-sqlite3mc", environmentDescriptor.storageDriver());
    assertEquals("sqlite", environmentDescriptor.storageEngine());
    assertEquals("required", environmentDescriptor.bookProtectionMode());
    assertEquals("chacha20", environmentDescriptor.defaultBookCipher());
    assertEquals("managed-only", environmentDescriptor.sqliteLibraryMode());
    assertEquals("self-contained-bundle", environmentDescriptor.publicCliDistribution());
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
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(keyFilePath));
    JsonNode payload = new ObjectMapper().readTree(outputStream.toByteArray()).path("payload");
    assertEquals(
        keyFilePath.toAbsolutePath().normalize().toString(),
        payload.path("bookKeyFile").asString());
    assertEquals("base64url-no-padding", payload.path("encoding").asString());
    assertEquals(256, payload.path("entropyBits").asInt());
    assertEquals("0600", payload.path("permissions").asString());
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
            .contains("\"code\":\"book-not-initialized\""));
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
  void run_routesCommandsThroughSelectedBookWorkflow() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
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
                List.of(
                    new DeclaredAccount(
                        new AccountCode("1000"),
                        new AccountName("Cash"),
                        NormalBalance.DEBIT,
                        true,
                        Instant.parse("2026-04-07T12:00:00Z")))),
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
              bookKeyFilePath.toString()
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

    assertEquals(List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.openBookAccesses());
    assertEquals(
        List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.declareAccountAccesses());
    assertEquals(
        List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.listAccountAccesses());
    assertEquals(List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.preflightAccesses());
    assertEquals(List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.commitAccesses());
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
            new ListAccountsResult.Listed(List.of()),
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
            new ListAccountsResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
            new PostEntryResult.Rejected(
                new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()),
            new PostEntryResult.Rejected(
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
            new ExplodingWorkflow("Failed to open SQLite book connection."));

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
            new ExplodingWorkflow("FINGRIND_SQLITE_LIBRARY is not configured."));

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
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("Run the published FinGrind bundle via bin/fingrind"));
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
                "fingrind.bundle.home did not resolve a bundled SQLite library."));

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
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("Run the published FinGrind bundle via bin/fingrind"));
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
            new ExplodingWorkflow("bin/fingrind must be used from the extracted bundle root."));

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
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("Run the published FinGrind bundle via bin/fingrind"));
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
            new ExplodingWorkflow("boom"));

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
  void run_mapsGenericIllegalArgumentExceptionToInvalidRequest() throws IOException {
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

    assertEquals(2, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"invalid-request\""));
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
            new ListAccountsResult.Listed(List.of()),
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
            new ListAccountsResult.Listed(List.of()),
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
    Files.writeString(keyFilePath, keyText, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        keyFilePath, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);
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
    private final List<BookAccess> preflightAccesses = new ArrayList<>();
    private final List<BookAccess> commitAccesses = new ArrayList<>();
    private final OpenBookResult openBookResult;
    private final RekeyBookResult rekeyBookResult;
    private final DeclareAccountResult declareAccountResult;
    private final ListAccountsResult listAccountsResult;
    private final PostEntryResult preflightResult;
    private final PostEntryResult commitResult;

    private RecordingWorkflow(
        OpenBookResult openBookResult,
        RekeyBookResult rekeyBookResult,
        DeclareAccountResult declareAccountResult,
        ListAccountsResult listAccountsResult,
        PostEntryResult preflightResult,
        PostEntryResult commitResult) {
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
    public ListAccountsResult listAccounts(BookAccess bookAccess) {
      listAccountAccesses.add(bookAccess);
      return listAccountsResult;
    }

    @Override
    public PostEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
      preflightAccesses.add(bookAccess);
      return preflightResult;
    }

    @Override
    public PostEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
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
          || !preflightAccesses.isEmpty()
          || !commitAccesses.isEmpty();
    }
  }

  /** Workflow stub that always throws the same runtime failure. */
  private static final class ExplodingWorkflow implements FinGrindCli.BookWorkflow {
    private final String message;

    private ExplodingWorkflow(String message) {
      this.message = message;
    }

    @Override
    public OpenBookResult openBook(BookAccess bookAccess) {
      throw new IllegalStateException(message);
    }

    @Override
    public RekeyBookResult rekeyBook(
        BookAccess bookAccess, BookAccess.PassphraseSource replacementPassphraseSource) {
      throw new IllegalStateException(message);
    }

    @Override
    public DeclareAccountResult declareAccount(
        BookAccess bookAccess, DeclareAccountCommand command) {
      throw new IllegalStateException(message);
    }

    @Override
    public ListAccountsResult listAccounts(BookAccess bookAccess) {
      throw new IllegalStateException(message);
    }

    @Override
    public PostEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
      throw new IllegalStateException(message);
    }

    @Override
    public PostEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
      throw new IllegalStateException(message);
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
    public ListAccountsResult listAccounts(BookAccess bookAccess) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public PostEntryResult preflight(BookAccess bookAccess, PostEntryCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }

    @Override
    public PostEntryResult commit(BookAccess bookAccess, PostEntryCommand command) {
      throw new IllegalArgumentException("workflow boom");
    }
  }

  private static BookAccess bookAccess(Path bookFilePath, Path bookKeyFilePath) {
    return new BookAccess(bookFilePath, new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath));
  }
}
