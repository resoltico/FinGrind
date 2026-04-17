package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.MachineContract;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link CliResponseWriter}. */
class CliResponseWriterTest {
  @Test
  void writeVersion_writesOkEnvelope() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeVersion(
        new MachineContract.VersionDescriptor(
            "FinGrind",
            "0.9.0",
            "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"));

    JsonNode json = readJson(outputStream);
    assertEquals("ok", json.path("status").asString());
    assertEquals("0.9.0", json.path("payload").path("version").asString());
  }

  @Test
  void writeCapabilities_omitsNullEnvironmentFields() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeCapabilities(
        MachineContract.capabilities(
            new MachineContract.ApplicationIdentity(
                "FinGrind",
                "0.9.0",
                "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"),
            new MachineContract.EnvironmentDescriptor(
                "container-image",
                "self-contained-bundle",
                List.of(
                    "macos-aarch64",
                    "macos-x86_64",
                    "linux-x86_64",
                    "linux-aarch64",
                    "windows-x86_64"),
                List.of(),
                "26+",
                "sqlite-ffm-sqlite3mc",
                "sqlite",
                "required",
                "chacha20",
                "managed-only",
                "FINGRIND_SQLITE_LIBRARY",
                "fingrind.bundle.home",
                List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
                false,
                "3.53.0",
                "2.3.3",
                "unavailable",
                null,
                null,
                "system sqlite unavailable"),
            Instant.parse("2026-04-13T12:00:00Z")));

    JsonNode json = readJson(outputStream);
    JsonNode payload = json.path("payload");
    JsonNode environment = payload.path("environment");

    assertTrue(payload.has("preflightSemantics"));
    assertTrue(payload.has("currencyModel"));
    assertEquals("required", environment.path("bookProtectionMode").asString());
    assertEquals("container-image", environment.path("runtimeDistribution").asString());
    assertEquals("self-contained-bundle", environment.path("publicCliDistribution").asString());
    assertEquals(
        "[\"macos-aarch64\",\"macos-x86_64\",\"linux-x86_64\",\"linux-aarch64\",\"windows-x86_64\"]",
        environment.path("supportedPublicCliBundleTargets").toString());
    assertEquals("[]", environment.path("unsupportedPublicCliOperatingSystems").toString());
    assertFalse(environment.has("loadedSqliteVersion"));
    assertFalse(environment.has("loadedSqlite3mcVersion"));
  }

  @Test
  void writeGenerateBookKeyFileResult_writesNonSecretMetadataEnvelope() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeGenerateBookKeyFileResult(
        new dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator.GeneratedKeyFile(
            Path.of("secrets").resolve("entity.book-key"), "base64url-no-padding", 256, "0600"));

    JsonNode json = readJson(outputStream);
    assertEquals("ok", json.path("status").asString());
    assertEquals(
        Path.of("secrets").resolve("entity.book-key").toAbsolutePath().normalize().toString(),
        json.path("payload").path("bookKeyFile").asString());
    assertEquals("base64url-no-padding", json.path("payload").path("encoding").asString());
    assertEquals(256, json.path("payload").path("entropyBits").asInt());
    assertEquals("0600", json.path("payload").path("permissions").asString());
  }

  @Test
  void writeFailure_writesErrorEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeFailure("invalid-request", "bad request");

    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"error\""));
  }

  @Test
  void writePostEntryResult_writesPreflightEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.PreflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")));

    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"status\":\"preflight-accepted\""));
  }

  @Test
  void writePostEntryResult_writesCommittedEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.Committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z")));

    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"committed\""));
  }

  @Test
  void writePostEntryResult_writesRejectedEnvelopeWithStructuredDetails() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.Rejected(
            new IdempotencyKey("idem-1"),
            new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))));

    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"status\":\"rejected\""));
    assertTrue(json.contains("\"code\":\"reversal-target-not-found\""));
    assertTrue(json.contains("\"priorPostingId\":\"posting-1\""));
  }

  @Test
  void writePostEntryResult_writesDuplicateIdempotencyRejectionWithoutDetails() {
    String json = rejectedJson(new PostingRejection.DuplicateIdempotencyKey());

    assertTrue(json.contains("\"code\":\"duplicate-idempotency-key\""));
    assertTrue(json.contains("same idempotency key"));
    assertFalse(json.contains("\"details\""));
  }

  @Test
  void writePostEntryResult_writesReversalReasonRequiredRejection() {
    String json = rejectedJson(new PostingRejection.ReversalReasonRequired());

    assertTrue(json.contains("\"code\":\"reversal-reason-required\""));
    assertTrue(json.contains("must include a human-readable reason"));
  }

  @Test
  void writePostEntryResult_writesReversalReasonForbiddenRejection() {
    String json = rejectedJson(new PostingRejection.ReversalReasonForbidden());

    assertTrue(json.contains("\"code\":\"reversal-reason-forbidden\""));
    assertTrue(json.contains("only permitted when a reversal target is present"));
  }

  @Test
  void writePostEntryResult_writesReversalAlreadyExistsRejection() {
    String json =
        rejectedJson(new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1")));

    assertTrue(json.contains("\"code\":\"reversal-already-exists\""));
    assertTrue(json.contains("already has a full reversal"));
    assertTrue(json.contains("\"priorPostingId\":\"posting-1\""));
  }

  @Test
  void writePostEntryResult_writesReversalDoesNotNegateTargetRejection() {
    String json =
        rejectedJson(new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1")));

    assertTrue(json.contains("\"code\":\"reversal-does-not-negate-target\""));
    assertTrue(json.contains("does not negate posting"));
    assertTrue(json.contains("\"priorPostingId\":\"posting-1\""));
  }

  @Test
  void writePostEntryResult_writesBookInitializationAndAccountRejections() {
    String bookJson = rejectedJson(new PostingRejection.BookNotInitialized());
    String accountStateJson =
        rejectedJson(
            new PostingRejection.AccountStateViolations(
                List.of(
                    new PostingRejection.UnknownAccount(new AccountCode("1000")),
                    new PostingRejection.InactiveAccount(new AccountCode("2000")))));

    assertTrue(bookJson.contains("\"code\":\"book-not-initialized\""));
    assertTrue(accountStateJson.contains("\"code\":\"account-state-violations\""));
    assertTrue(accountStateJson.contains("\"accountCode\":\"1000\""));
    assertTrue(accountStateJson.contains("\"code\":\"inactive-account\""));
  }

  @Test
  void writeOpenBookResult_writesSuccessEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeOpenBookResult(
        Path.of("book.sqlite"), new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")));

    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"status\":\"ok\""));
    assertTrue(json.contains("\"bookFile\""));
    assertTrue(json.contains("\"initializedAt\":\"2026-04-07T10:15:30Z\""));
  }

  @Test
  void writeOpenBookResult_writesAlreadyInitializedAndSchemaConflictRejections() {
    String alreadyInitializedJson =
        openBookRejectedJson(new BookAdministrationRejection.BookAlreadyInitialized());
    String schemaConflictJson =
        openBookRejectedJson(new BookAdministrationRejection.BookContainsSchema());

    assertTrue(alreadyInitializedJson.contains("\"code\":\"book-already-initialized\""));
    assertTrue(alreadyInitializedJson.contains("already initialized"));
    assertTrue(schemaConflictJson.contains("\"code\":\"book-contains-schema\""));
    assertTrue(schemaConflictJson.contains("already contains schema objects"));
  }

  @Test
  void writeRekeyBookResult_writesSuccessAndRejectionEnvelopes() {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));

    successWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books").resolve("entity.sqlite")));

    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertTrue(successJson.contains("\"status\":\"ok\""));
    assertTrue(successJson.contains("\"bookFile\""));

    ByteArrayOutputStream rejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter rejectionWriter = new CliResponseWriter(utf8PrintStream(rejectionOutput));

    rejectionWriter.writeRekeyBookResult(
        new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized()));

    String rejectionJson = rejectionOutput.toString(StandardCharsets.UTF_8);
    assertTrue(rejectionJson.contains("\"status\":\"rejected\""));
    assertTrue(rejectionJson.contains("\"code\":\"book-not-initialized\""));
  }

  @Test
  void writeDeclareAccountAndListAccountsResults_writeSuccessAndRejectionEnvelopes() {
    DeclaredAccount declaredAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    ByteArrayOutputStream declareSuccessOutput = new ByteArrayOutputStream();
    CliResponseWriter declareSuccessWriter =
        new CliResponseWriter(utf8PrintStream(declareSuccessOutput));
    declareSuccessWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Declared(declaredAccount));

    ByteArrayOutputStream listSuccessOutput = new ByteArrayOutputStream();
    CliResponseWriter listSuccessWriter = new CliResponseWriter(utf8PrintStream(listSuccessOutput));
    listSuccessWriter.writeListAccountsResult(
        new ListAccountsResult.Listed(
            new AccountPage(java.util.List.of(declaredAccount), 50, 0, false)));

    String declareSuccessJson = declareSuccessOutput.toString(StandardCharsets.UTF_8);
    assertTrue(declareSuccessJson.contains("\"accountName\":\"Cash\""));
    assertTrue(declareSuccessJson.contains("\"declaredAt\":\"2026-04-07T10:15:30Z\""));

    String listSuccessJson = listSuccessOutput.toString(StandardCharsets.UTF_8);
    assertTrue(listSuccessJson.contains("\"limit\":50"));
    assertTrue(listSuccessJson.contains("\"offset\":0"));
    assertTrue(listSuccessJson.contains("\"hasMore\":false"));
    assertTrue(listSuccessJson.contains("\"accountName\":\"Cash\""));

    ByteArrayOutputStream declareRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter declareRejectionWriter =
        new CliResponseWriter(utf8PrintStream(declareRejectionOutput));
    declareRejectionWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()));
    ByteArrayOutputStream declareConflictOutput = new ByteArrayOutputStream();
    CliResponseWriter declareConflictWriter =
        new CliResponseWriter(utf8PrintStream(declareConflictOutput));
    declareConflictWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Rejected(
            new BookAdministrationRejection.NormalBalanceConflict(
                new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT)));

    ByteArrayOutputStream listRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter listRejectionWriter =
        new CliResponseWriter(utf8PrintStream(listRejectionOutput));
    listRejectionWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()));

    assertTrue(
        declareRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"book-not-initialized\""));
    String declareConflictJson = declareConflictOutput.toString(StandardCharsets.UTF_8);
    assertTrue(declareConflictJson.contains("\"code\":\"account-normal-balance-conflict\""));
    assertTrue(declareConflictJson.contains("\"accountCode\":\"1000\""));
    assertTrue(declareConflictJson.contains("\"existingNormalBalance\":\"DEBIT\""));
    assertTrue(declareConflictJson.contains("\"requestedNormalBalance\":\"CREDIT\""));
    assertTrue(declareConflictJson.contains("already exists with normal balance"));
    assertTrue(
        listRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"book-not-initialized\""));
  }

  @Test
  void writeQueryResults_writeSuccessAndRejectionEnvelopes() {
    PostingFact postingFact = postingFact();
    AccountBalanceSnapshot balanceSnapshot =
        new AccountBalanceSnapshot(
            new DeclaredAccount(
                new AccountCode("1000"),
                new AccountName("Cash"),
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z")),
            java.util.Optional.of(LocalDate.parse("2026-04-01")),
            java.util.Optional.of(LocalDate.parse("2026-04-30")),
            List.of(
                new CurrencyBalance(
                    money("EUR", "10.00"),
                    money("EUR", "4.00"),
                    money("EUR", "6.00"),
                    NormalBalance.DEBIT)));

    ByteArrayOutputStream inspectionOutput = new ByteArrayOutputStream();
    CliResponseWriter inspectionWriter = new CliResponseWriter(utf8PrintStream(inspectionOutput));
    inspectionWriter.writeBookInspection(
        Path.of("book.sqlite"),
        new BookInspection(
            BookInspection.Status.INITIALIZED,
            true,
            true,
            false,
            1_179_079_236,
            1,
            1,
            "hard-break-no-migration",
            Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream missingInspectionOutput = new ByteArrayOutputStream();
    CliResponseWriter missingInspectionWriter =
        new CliResponseWriter(utf8PrintStream(missingInspectionOutput));
    missingInspectionWriter.writeBookInspection(
        Path.of("missing.sqlite"),
        new BookInspection(
            BookInspection.Status.MISSING,
            false,
            true,
            true,
            null,
            null,
            1,
            "hard-break-no-migration",
            null));
    ByteArrayOutputStream getPostingOutput = new ByteArrayOutputStream();
    CliResponseWriter getPostingWriter = new CliResponseWriter(utf8PrintStream(getPostingOutput));
    getPostingWriter.writeGetPostingResult(new GetPostingResult.Found(postingFact));
    ByteArrayOutputStream getPostingRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter getPostingRejectionWriter =
        new CliResponseWriter(utf8PrintStream(getPostingRejectionOutput));
    getPostingRejectionWriter.writeGetPostingResult(
        new GetPostingResult.Rejected(
            new BookQueryRejection.PostingNotFound(new PostingId("posting-9"))));

    ByteArrayOutputStream listPostingsOutput = new ByteArrayOutputStream();
    CliResponseWriter listPostingsWriter =
        new CliResponseWriter(utf8PrintStream(listPostingsOutput));
    listPostingsWriter.writeListPostingsResult(
        new ListPostingsResult.Listed(new PostingPage(List.of(postingFact), 10, 0, false)));
    ByteArrayOutputStream listPostingsRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter listPostingsRejectionWriter =
        new CliResponseWriter(utf8PrintStream(listPostingsRejectionOutput));
    listPostingsRejectionWriter.writeListPostingsResult(
        new ListPostingsResult.Rejected(
            new BookQueryRejection.UnknownAccount(new AccountCode("9999"))));

    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    CliResponseWriter balanceWriter = new CliResponseWriter(utf8PrintStream(balanceOutput));
    balanceWriter.writeAccountBalanceResult(new AccountBalanceResult.Reported(balanceSnapshot));
    ByteArrayOutputStream balanceRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter balanceRejectionWriter =
        new CliResponseWriter(utf8PrintStream(balanceRejectionOutput));
    balanceRejectionWriter.writeAccountBalanceResult(
        new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()));

    assertTrue(inspectionOutput.toString(StandardCharsets.UTF_8).contains("\"bookFile\""));
    assertTrue(
        inspectionOutput.toString(StandardCharsets.UTF_8).contains("\"state\":\"INITIALIZED\""));
    assertFalse(
        missingInspectionOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));
    assertTrue(
        getPostingOutput.toString(StandardCharsets.UTF_8).contains("\"reason\":\"full reversal\""));
    assertTrue(
        getPostingOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"priorPostingId\":\"posting-0\""));
    assertTrue(
        getPostingRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"posting-not-found\""));
    assertTrue(
        getPostingRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"postingId\":\"posting-9\""));
    assertTrue(listPostingsOutput.toString(StandardCharsets.UTF_8).contains("\"postings\":["));
    assertTrue(
        listPostingsRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"accountCode\":\"9999\""));
    assertTrue(
        balanceOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"effectiveDateFrom\":\"2026-04-01\""));
    assertTrue(
        balanceOutput.toString(StandardCharsets.UTF_8).contains("\"balanceSide\":\"DEBIT\""));
    assertTrue(
        balanceRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"book-not-initialized\""));
  }

  private static PostingFact postingFact() {
    return new PostingFact(
        new PostingId("posting-1"),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("EUR", "10.00")))),
        java.util.Optional.of(new ReversalReference(new PostingId("posting-0"))),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                java.util.Optional.of(new CorrelationId("corr-1")),
                java.util.Optional.of(new ReversalReason("full reversal"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static Money money(String currencyCode, String amount) {
    return new Money(new CurrencyCode(currencyCode), new BigDecimal(amount));
  }

  private static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }

  private static String rejectedJson(PostingRejection rejection) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.Rejected(new IdempotencyKey("idem-1"), rejection));

    return outputStream.toString(StandardCharsets.UTF_8);
  }

  private static String openBookRejectedJson(BookAdministrationRejection rejection) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeOpenBookResult(
        Path.of("book.sqlite"), new OpenBookResult.Rejected(rejection));

    return outputStream.toString(StandardCharsets.UTF_8);
  }

  private static JsonNode readJson(ByteArrayOutputStream outputStream) throws IOException {
    return new ObjectMapper().readTree(outputStream.toString(StandardCharsets.UTF_8));
  }
}
