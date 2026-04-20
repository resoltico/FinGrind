package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookMigrationPolicy;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.ContractDiscovery;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerExecutionJournal;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.LedgerStepId;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.MachineContract;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link CliResponseWriter}. */
class CliResponseWriterTest {
  @Test
  void planRejectionStatus_rejectsSucceededPlansAndMapsFailures() {
    assertEquals("plan-rejected", CliResponseWriter.planRejectionStatus(LedgerPlanStatus.REJECTED));
    assertEquals(
        "plan-assertion-failed",
        CliResponseWriter.planRejectionStatus(LedgerPlanStatus.ASSERTION_FAILED));
    assertThrows(
        IllegalArgumentException.class,
        () -> CliResponseWriter.planRejectionStatus(LedgerPlanStatus.SUCCEEDED));
  }

  @Test
  void writeLedgerPlanResult_emitsTypedAndGroupedFacts() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.Succeeded balanceEntry =
        new LedgerJournalEntry.Succeeded(
            stepId("balance"),
            LedgerStepKind.ACCOUNT_BALANCE,
            null,
            startedAt,
            finishedAt,
            List.of(
                LedgerFact.text("accountCode", "1000"),
                LedgerFact.count("bucketCount", 1),
                LedgerFact.group(
                    "balance",
                    List.of(
                        LedgerFact.text("currencyCode", "EUR"),
                        LedgerFact.text("netAmount", "10.00"),
                        LedgerFact.text("balanceSide", "DEBIT")))));

    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.Succeeded(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(balanceEntry))));

    JsonNode facts =
        readJson(outputStream).path("payload").path("journal").path("steps").get(0).path("facts");

    assertEquals("text", facts.get(0).path("kind").asText());
    assertEquals("accountCode", facts.get(0).path("name").asText());
    assertEquals("1000", facts.get(0).path("value").asText());
    assertEquals("count", facts.get(1).path("kind").asText());
    assertEquals(1, facts.get(1).path("value").asInt());
    assertEquals("group", facts.get(2).path("kind").asText());
    assertEquals("balance", facts.get(2).path("name").asText());
    assertEquals("currencyCode", facts.get(2).path("facts").get(0).path("name").asText());
    assertEquals("EUR", facts.get(2).path("facts").get(0).path("value").asText());
  }

  @Test
  void writeLedgerPlanResult_writesRejectedEnvelopeForRejectedPlans() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.Rejected rejectedEntry =
        new LedgerJournalEntry.Rejected(
            stepId("declare-cash"),
            LedgerStepKind.DECLARE_ACCOUNT,
            null,
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure(
                "administration-book-not-initialized", "Book is not initialized.", List.of()));

    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.Rejected(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(rejectedEntry))));

    JsonNode json = readJson(outputStream);

    assertEquals("plan-rejected", json.path("status").asText());
    assertEquals("administration-book-not-initialized", json.path("code").asText());
    assertEquals("rejected", json.path("details").path("plan").path("status").asText());
  }

  @Test
  void writeLedgerPlanResult_writesRejectedEnvelopeForAssertionFailedPlans() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.AssertionFailed assertionFailedEntry =
        new LedgerJournalEntry.AssertionFailed(
            stepId("assert-balance"),
            LedgerStepKind.ASSERT,
            LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure("assertion-failed", "Balance mismatch.", List.of()));

    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.AssertionFailed(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(assertionFailedEntry))));

    JsonNode json = readJson(outputStream);

    assertEquals("plan-assertion-failed", json.path("status").asText());
    assertEquals("assertion-failed", json.path("code").asText());
    assertEquals("assertion-failed", json.path("details").path("plan").path("status").asText());
  }

  @Test
  void writeJson_serializationFailureDoesNotEmitPartialOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    SelfReferentialValue cyclic = new SelfReferentialValue();

    assertThrows(RuntimeException.class, () -> responseWriter.writeJson(cyclic, false));
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
  }

  @Test
  void writeJson_writesStandaloneJsonPayload() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeJson(Map.of("status", "ok", "count", 2), false);

    JsonNode json = readJson(outputStream);

    assertEquals("ok", json.path("status").asText());
    assertEquals(2, json.path("count").asInt());
  }

  @Test
  void writeAdministrativeAndWriteSuccesses_supportHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeGenerateBookKeyFileResult(
        new dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator.GeneratedKeyFile(
            Path.of("keys/book.key"), "base64url-no-padding", 256, "0600"),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Book Key File Generated"));

    outputStream.reset();
    responseWriter.writeOpenBookResult(
        Path.of("books/book.sqlite"),
        new OpenBookResult.Opened(Instant.parse("2026-04-17T10:15:30Z")),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Book Initialized"));

    outputStream.reset();
    responseWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books/book.sqlite")), OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Book Rekeyed"));

    outputStream.reset();
    responseWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Declared(
            new DeclaredAccount(
                new AccountCode("1000"),
                new AccountName("Cash"),
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-17T10:15:30Z"))),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Account Declared"));

    outputStream.reset();
    responseWriter.writePostEntryResult(
        new PostEntryResult.PreflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-17")),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Entry Preflight Accepted"));

    outputStream.reset();
    responseWriter.writePostEntryResult(
        new PostEntryResult.Committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-17"),
            Instant.parse("2026-04-17T10:15:31Z")),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Entry Committed"));
  }

  @Test
  void writeAdministrativeAndWriteSuccesses_rejectCsvOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeGenerateBookKeyFileResult(
                new dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator.GeneratedKeyFile(
                    Path.of("keys/book.key"), "base64url-no-padding", 256, "0600"),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeOpenBookResult(
                Path.of("books/book.sqlite"),
                new OpenBookResult.Opened(Instant.parse("2026-04-17T10:15:30Z")),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeRekeyBookResult(
                new RekeyBookResult.Rekeyed(Path.of("books/book.sqlite")), OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeDeclareAccountResult(
                new DeclareAccountResult.Declared(
                    new DeclaredAccount(
                        new AccountCode("1000"),
                        new AccountName("Cash"),
                        NormalBalance.DEBIT,
                        true,
                        Instant.parse("2026-04-17T10:15:30Z"))),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writePostEntryResult(
                new PostEntryResult.PreflightAccepted(
                    new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-17")),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writePostEntryResult(
                new PostEntryResult.Committed(
                    new PostingId("posting-1"),
                    new IdempotencyKey("idem-1"),
                    LocalDate.parse("2026-04-17"),
                    Instant.parse("2026-04-17T10:15:31Z")),
                OutputMode.CSV));
  }

  @Test
  void writeAdministrativeAndWriteRejections_supportHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeOpenBookResult(
        Path.of("books/book.sqlite"),
        new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Rejected"));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("book-already-initialized"));

    outputStream.reset();
    responseWriter.writePostEntryResult(
        new PostEntryResult.PreflightRejected(
            new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey()),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Idempotency key"));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("duplicate-idempotency-key"));
  }

  @Test
  void writeFailure_supportsHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeFailure(
        new CliFailure("invalid-request", "Unsupported argument: --bogus", "Try help", "--bogus"),
        OutputMode.HUMAN);

    String text = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("Error"));
    assertTrue(text.contains("invalid-request"));
    assertTrue(text.contains("Unsupported argument: --bogus"));
    assertTrue(text.contains("Try help"));
  }

  @Test
  void writeQueryRejection_keepsJsonEnvelopeOutsideHumanMode() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
        OutputMode.JSON);

    JsonNode json = readJson(outputStream);
    assertEquals("rejected", json.path("status").asText());
    assertEquals("query-book-not-initialized", json.path("code").asText());
  }

  @Test
  void writeQueryRejection_supportsHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
        OutputMode.HUMAN);

    String text = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("Rejected"));
    assertTrue(text.contains("query-book-not-initialized"));
  }

  @Test
  void queryRejectionWriter_coversJsonAndHumanBranches() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel outputChannel = new CliOutputChannel(utf8PrintStream(outputStream));
    CliResponseJsonModels.RejectedEnvelope envelope =
        new CliResponseJsonModels.RejectedEnvelope(
            "rejected", "query-book-not-initialized", "The book is not initialized.", null, null);

    outputChannel.writeQueryRejection(OutputMode.HUMAN, envelope);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Rejected"));

    outputStream.reset();
    outputChannel.writeQueryRejection(OutputMode.JSON, envelope);
    JsonNode json = readJson(outputStream);
    assertEquals("rejected", json.path("status").asText());
    assertEquals("query-book-not-initialized", json.path("code").asText());

    outputStream.reset();
    outputChannel.writeQueryRejection(OutputMode.CSV, envelope);
    json = readJson(outputStream);
    assertEquals("rejected", json.path("status").asText());
    assertEquals("query-book-not-initialized", json.path("code").asText());
  }

  @Test
  void writeVersion_writesOkEnvelope() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeVersion(
        new ContractDiscovery.VersionDescriptor(
            "FinGrind",
            "0.9.0",
            "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"));

    JsonNode json = readJson(outputStream);
    assertEquals("ok", json.path("status").asString());
    assertEquals("0.9.0", json.path("payload").path("version").asString());
  }

  @Test
  void writeHelp_supportsJsonAndHumanButRejectsCsv() throws IOException {
    ContractDiscovery.HelpDescriptor helpDescriptor =
        MachineContract.help(
            new ContractDiscovery.ApplicationIdentity(
                "FinGrind",
                "0.9.0",
                "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"),
            new ContractDiscovery.EnvironmentDescriptor(
                "self-contained-bundle",
                "self-contained-bundle",
                ProtocolCatalog.supportedPublicCliBundleTargets(),
                ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
                ProtocolCatalog.sourceCheckoutJava(),
                "sqlite-ffm-sqlite3mc",
                "sqlite",
                "required",
                "chacha20",
                "managed-only",
                "FINGRIND_SQLITE_LIBRARY",
                "fingrind.bundle.home",
                List.of("THREADSAFE=1"),
                true,
                "3.53.0",
                "2.3.3",
                "loaded",
                "3.53.0",
                "2.3.3",
                null));
    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    CliResponseWriter jsonWriter = new CliResponseWriter(utf8PrintStream(jsonOutput));

    jsonWriter.writeHelp(helpDescriptor);
    assertEquals("ok", readJson(jsonOutput).path("status").asText());

    ByteArrayOutputStream humanOutput = new ByteArrayOutputStream();
    CliResponseWriter humanWriter = new CliResponseWriter(utf8PrintStream(humanOutput));
    humanWriter.writeHelp(helpDescriptor, OutputMode.HUMAN);
    assertTrue(humanOutput.toString(StandardCharsets.UTF_8).contains("FinGrind Help"));

    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    CliResponseWriter csvWriter = new CliResponseWriter(utf8PrintStream(csvOutput));
    assertThrows(
        IllegalArgumentException.class, () -> csvWriter.writeHelp(helpDescriptor, OutputMode.CSV));
  }

  @Test
  void writeCapabilities_omitsNullEnvironmentFields() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeCapabilities(
        MachineContract.capabilities(
            new ContractDiscovery.ApplicationIdentity(
                "FinGrind",
                "0.9.0",
                "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"),
            new ContractDiscovery.EnvironmentDescriptor(
                "container-image",
                "self-contained-bundle",
                ProtocolCatalog.supportedPublicCliBundleTargets(),
                ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
                ProtocolCatalog.sourceCheckoutJava(),
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
        ProtocolCatalog.supportedPublicCliBundleTargets(),
        readTextArray(environment.path("supportedPublicCliBundleTargets")));
    assertEquals(
        ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
        readTextArray(environment.path("unsupportedPublicCliOperatingSystems")));
    assertFalse(environment.has("loadedSqliteVersion"));
    assertFalse(environment.has("loadedSqlite3mcVersion"));
  }

  @Test
  void writeCapabilities_supportsHumanButRejectsCsv() {
    ContractDiscovery.CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(
            new ContractDiscovery.ApplicationIdentity(
                "FinGrind",
                "0.9.0",
                "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence"),
            new ContractDiscovery.EnvironmentDescriptor(
                "self-contained-bundle",
                "self-contained-bundle",
                ProtocolCatalog.supportedPublicCliBundleTargets(),
                ProtocolCatalog.unsupportedPublicCliOperatingSystems(),
                ProtocolCatalog.sourceCheckoutJava(),
                "sqlite-ffm-sqlite3mc",
                "sqlite",
                "required",
                "chacha20",
                "managed-only",
                "FINGRIND_SQLITE_LIBRARY",
                "fingrind.bundle.home",
                List.of("THREADSAFE=1"),
                true,
                "3.53.0",
                "2.3.3",
                "loaded",
                "3.53.0",
                "2.3.3",
                null),
            Instant.parse("2026-04-13T12:00:00Z"));
    ByteArrayOutputStream humanOutput = new ByteArrayOutputStream();
    CliResponseWriter humanWriter = new CliResponseWriter(utf8PrintStream(humanOutput));

    humanWriter.writeCapabilities(capabilities, OutputMode.HUMAN);
    assertTrue(humanOutput.toString(StandardCharsets.UTF_8).contains("FinGrind Capabilities"));

    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    CliResponseWriter csvWriter = new CliResponseWriter(utf8PrintStream(csvOutput));
    assertThrows(
        IllegalArgumentException.class,
        () -> csvWriter.writeCapabilities(capabilities, OutputMode.CSV));
  }

  @Test
  void writeVersion_supportsHumanButRejectsCsv() {
    ContractDiscovery.VersionDescriptor versionDescriptor =
        new ContractDiscovery.VersionDescriptor(
            "FinGrind",
            "0.9.0",
            "Finance-grade bookkeeping kernel with an agent-first CLI and SQLite-first persistence");
    ByteArrayOutputStream humanOutput = new ByteArrayOutputStream();
    CliResponseWriter humanWriter = new CliResponseWriter(utf8PrintStream(humanOutput));

    humanWriter.writeVersion(versionDescriptor, OutputMode.HUMAN);
    assertTrue(humanOutput.toString(StandardCharsets.UTF_8).contains("Version"));

    ByteArrayOutputStream csvOutput = new ByteArrayOutputStream();
    CliResponseWriter csvWriter = new CliResponseWriter(utf8PrintStream(csvOutput));
    assertThrows(
        IllegalArgumentException.class,
        () -> csvWriter.writeVersion(versionDescriptor, OutputMode.CSV));
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
        new PostEntryResult.CommitRejected(
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

    assertTrue(bookJson.contains("\"code\":\"posting-book-not-initialized\""));
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
    assertTrue(rejectionJson.contains("\"code\":\"administration-book-not-initialized\""));
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
            .contains("\"code\":\"administration-book-not-initialized\""));
    String declareConflictJson = declareConflictOutput.toString(StandardCharsets.UTF_8);
    assertTrue(declareConflictJson.contains("\"code\":\"account-normal-balance-conflict\""));
    assertTrue(declareConflictJson.contains("\"accountCode\":\"1000\""));
    assertTrue(declareConflictJson.contains("\"existingNormalBalance\":\"DEBIT\""));
    assertTrue(declareConflictJson.contains("\"requestedNormalBalance\":\"CREDIT\""));
    assertTrue(declareConflictJson.contains("already exists with normal balance"));
    assertTrue(
        listRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"query-book-not-initialized\""));
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
                    BalanceSide.DEBIT)));

    ByteArrayOutputStream inspectionOutput = new ByteArrayOutputStream();
    CliResponseWriter inspectionWriter = new CliResponseWriter(utf8PrintStream(inspectionOutput));
    inspectionWriter.writeBookInspection(
        Path.of("book.sqlite"),
        new BookInspection.Initialized(
            1_179_079_236,
            1,
            1,
            BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
            Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream missingInspectionOutput = new ByteArrayOutputStream();
    CliResponseWriter missingInspectionWriter =
        new CliResponseWriter(utf8PrintStream(missingInspectionOutput));
    missingInspectionWriter.writeBookInspection(
        Path.of("missing.sqlite"),
        new BookInspection.Missing(1, BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
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
        new ListPostingsResult.Listed(
            new PostingPage(List.of(postingFact), 10, java.util.Optional.empty())));
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
        inspectionOutput.toString(StandardCharsets.UTF_8).contains("\"state\":\"initialized\""));
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
            .contains("\"code\":\"query-book-not-initialized\""));
  }

  @Test
  void writeQueryResults_supportHumanAndCsvOutputModes() {
    PostingFact postingFact = postingFact();
    AccountBalanceSnapshot balanceSnapshot =
        new AccountBalanceSnapshot(
            declaredCashAccount(),
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            List.of(currencyBalance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT)));

    ByteArrayOutputStream postingRegisterHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterHumanOutput))
        .writeListPostingsResult(
            new ListPostingsResult.Listed(
                new PostingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.HUMAN);
    String postingRegisterHuman = postingRegisterHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(postingRegisterHuman.contains("Posting id"));
    assertTrue(postingRegisterHuman.contains("10.00"));
    assertTrue(postingRegisterHuman.contains("posting-1"));

    ByteArrayOutputStream postingRegisterCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(postingRegisterCsvOutput))
        .writeListPostingsResult(
            new ListPostingsResult.Listed(
                new PostingPage(List.of(postingFact), 10, Optional.empty())),
            OutputMode.CSV);
    String postingRegisterCsv = postingRegisterCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        postingRegisterCsv.startsWith(
            "effectiveDate,recordedAt,postingId,currencyCode,totalAmount,accountCodes,reversalTarget"));
    assertTrue(postingRegisterCsv.contains("2026-04-07,2026-04-07T10:15:30Z,posting-1,EUR,10.00"));

    ByteArrayOutputStream balanceHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(balanceHumanOutput))
        .writeAccountBalanceResult(
            new AccountBalanceResult.Reported(balanceSnapshot), OutputMode.HUMAN);
    String balanceHuman = balanceHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(balanceHuman.contains("Account        : 1000"));
    assertTrue(balanceHuman.contains("Debit total"));
    assertTrue(balanceHuman.contains("10.00"));
    assertTrue(balanceHuman.contains("6.00"));

    ByteArrayOutputStream balanceCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(balanceCsvOutput))
        .writeAccountBalanceResult(
            new AccountBalanceResult.Reported(balanceSnapshot), OutputMode.CSV);
    String balanceCsv = balanceCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        balanceCsv.startsWith(
            "accountCode,accountName,normalBalance,effectiveDateFrom,effectiveDateTo,currencyCode,debitTotal,creditTotal,netAmount,balanceSide"));
    assertTrue(
        balanceCsv.contains("1000,Cash,DEBIT,2026-04-01,2026-04-30,EUR,10.00,4.00,6.00,DEBIT"));
  }

  @Test
  void writeReportResults_supportJsonHumanAndCsvOutputModes() throws IOException {
    TrialBalanceReport trialBalanceReport =
        new TrialBalanceReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            List.of(
                new TrialBalanceRow(
                    declaredCashAccount(),
                    currencyBalance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT))));
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            declaredCashAccount(),
            EffectiveDateRange.of(
                Optional.of(LocalDate.parse("2026-04-01")),
                Optional.of(LocalDate.parse("2026-04-30"))),
            List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
            List.of(
                new AccountLedgerEntry(
                    postingFact(),
                    currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                    money("EUR", "10.00"),
                    BalanceSide.DEBIT)),
            List.of(currencyBalance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
    PeriodSummaryReport periodSummaryReport =
        new PeriodSummaryReport(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            1,
            2,
            2,
            List.of(
                new PeriodCurrencySummary(
                    currencyBalance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO))),
            List.of(
                new PeriodAccountActivityRow(
                    declaredCashAccount(),
                    currencyBalance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT))));

    ByteArrayOutputStream trialBalanceJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(trialBalanceJsonOutput))
        .writeTrialBalanceResult(
            new TrialBalanceResult.Reported(trialBalanceReport), OutputMode.JSON);
    JsonNode trialBalanceJson = readJson(trialBalanceJsonOutput);
    assertEquals("ok", trialBalanceJson.path("status").asText());
    assertEquals("2026-04-30", trialBalanceJson.path("payload").path("effectiveDateTo").asText());
    assertEquals(
        "1000", trialBalanceJson.path("payload").path("rows").get(0).path("accountCode").asText());
    assertEquals(
        "10", trialBalanceJson.path("payload").path("rows").get(0).path("debitTotal").asText());
    assertFalse(trialBalanceJson.toString().contains("\"value\""));

    ByteArrayOutputStream trialBalanceHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(trialBalanceHumanOutput))
        .writeTrialBalanceResult(
            new TrialBalanceResult.Reported(trialBalanceReport), OutputMode.HUMAN);
    String trialBalanceHuman = trialBalanceHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(trialBalanceHuman.contains("Effective date to : 2026-04-30"));
    assertTrue(trialBalanceHuman.contains("Account"));
    assertTrue(trialBalanceHuman.contains("6.00"));

    ByteArrayOutputStream accountLedgerHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountLedgerHumanOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.AccountLedgerResult.Reported(accountLedgerReport),
            OutputMode.HUMAN);
    String accountLedgerHuman = accountLedgerHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(accountLedgerHuman.contains("Opening balances : EUR 10.00 DEBIT"));
    assertTrue(accountLedgerHuman.contains("Running balance"));
    assertTrue(accountLedgerHuman.contains("posting-1"));

    ByteArrayOutputStream accountLedgerJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountLedgerJsonOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.AccountLedgerResult.Reported(accountLedgerReport),
            OutputMode.JSON);
    JsonNode accountLedgerJson = readJson(accountLedgerJsonOutput);
    assertEquals("1000", accountLedgerJson.path("payload").path("accountCode").asText());
    assertEquals(
        "2026-04-01", accountLedgerJson.path("payload").path("effectiveDateFrom").asText());
    assertEquals(
        "posting-1",
        accountLedgerJson.path("payload").path("entries").get(0).path("postingId").asText());
    assertEquals(
        "2000",
        accountLedgerJson
            .path("payload")
            .path("entries")
            .get(0)
            .path("counterpartAccounts")
            .get(0)
            .asText());
    assertFalse(accountLedgerJson.toString().contains("\"postingFact\""));

    ByteArrayOutputStream accountLedgerCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(accountLedgerCsvOutput))
        .writeAccountLedgerResult(
            new dev.erst.fingrind.contract.AccountLedgerResult.Reported(accountLedgerReport),
            OutputMode.CSV);
    String accountLedgerCsv = accountLedgerCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        accountLedgerCsv.startsWith(
            "accountCode,accountName,effectiveDateFrom,effectiveDateTo,postingId,effectiveDate,recordedAt,currencyCode,debitAmount,creditAmount,runningBalance,runningBalanceSide,counterpartAccounts"));
    assertTrue(
        accountLedgerCsv.contains(
            "1000,Cash,2026-04-01,2026-04-30,posting-1,2026-04-07,2026-04-07T10:15:30Z,EUR,10.00,0.00,10.00,DEBIT,2000"));

    ByteArrayOutputStream periodSummaryHumanOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryHumanOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.PeriodSummaryResult.Reported(periodSummaryReport),
            OutputMode.HUMAN);
    String periodSummaryHuman = periodSummaryHumanOutput.toString(StandardCharsets.UTF_8);
    assertTrue(periodSummaryHuman.contains("Posting count"));
    assertTrue(periodSummaryHuman.contains("Posting line count"));
    assertTrue(periodSummaryHuman.contains("10.00"));

    ByteArrayOutputStream periodSummaryJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryJsonOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.PeriodSummaryResult.Reported(periodSummaryReport),
            OutputMode.JSON);
    JsonNode periodSummaryJson = readJson(periodSummaryJsonOutput);
    assertEquals(
        "2026-04-01", periodSummaryJson.path("payload").path("effectiveDateFrom").asText());
    assertEquals(1, periodSummaryJson.path("payload").path("postingCount").asInt());
    assertEquals(
        "1000",
        periodSummaryJson
            .path("payload")
            .path("accountActivity")
            .get(0)
            .path("accountCode")
            .asText());
    assertFalse(periodSummaryJson.toString().contains("\"account\":{\"accountCode\""));

    ByteArrayOutputStream periodSummaryCsvOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(periodSummaryCsvOutput))
        .writePeriodSummaryResult(
            new dev.erst.fingrind.contract.PeriodSummaryResult.Reported(periodSummaryReport),
            OutputMode.CSV);
    String periodSummaryCsv = periodSummaryCsvOutput.toString(StandardCharsets.UTF_8);
    assertTrue(
        periodSummaryCsv.startsWith(
            "effectiveDateFrom,effectiveDateTo,postingCount,postingLineCount,accountsTouched,accountCode,accountName,normalBalance,currencyCode,debitTotal,creditTotal,netAmount,balanceSide"));
    assertTrue(
        periodSummaryCsv.contains(
            "2026-04-01,2026-04-30,1,2,2,1000,Cash,DEBIT,EUR,10.00,4.00,6.00,DEBIT"));
  }

  @Test
  void writeBookInspection_writesEveryExistingBookVariant() throws IOException {
    List<BookInspection> inspections =
        List.of(
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                1_179_079_236,
                0,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Existing(
                BookInspection.Status.FOREIGN_SQLITE,
                1_179_079_236,
                0,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Existing(
                BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
                1_179_079_236,
                2,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
            new BookInspection.Existing(
                BookInspection.Status.INCOMPLETE_FINGRIND,
                1_179_079_236,
                1,
                1,
                BookMigrationPolicy.SEQUENTIAL_IN_PLACE));
    List<String> states =
        List.of(
            "blank-sqlite", "foreign-sqlite", "unsupported-format-version", "incomplete-fingrind");

    for (int index = 0; index < inspections.size(); index++) {
      JsonNode payload = writeInspection(inspections.get(index));

      assertEquals(states.get(index), payload.path("state").asString());
      assertEquals(1_179_079_236, payload.path("applicationId").asInt());
      assertEquals(1, payload.path("supportedBookFormatVersion").asInt());
      assertEquals("sequential-in-place", payload.path("migrationPolicy").asString());
      assertFalse(payload.has("initializedAt"));
    }
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
        PostingLineage.reversal(
            new ReversalReference(new PostingId("posting-0")), new ReversalReason("full reversal")),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                java.util.Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static DeclaredAccount declaredCashAccount() {
    return new DeclaredAccount(
        new AccountCode("1000"),
        new AccountName("Cash"),
        NormalBalance.DEBIT,
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
  }

  private static CurrencyBalance currencyBalance(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      BalanceSide balanceSide) {
    return new CurrencyBalance(
        money(currencyCode, debitTotal),
        money(currencyCode, creditTotal),
        money(currencyCode, netAmount),
        balanceSide);
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
        new PostEntryResult.CommitRejected(new IdempotencyKey("idem-1"), rejection));

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

  private static LedgerPlanId planId(String value) {
    return new LedgerPlanId(value);
  }

  private static LedgerStepId stepId(String value) {
    return new LedgerStepId(value);
  }

  private static JsonNode writeInspection(BookInspection inspection) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeBookInspection(Path.of("book.sqlite"), inspection);
    return readJson(outputStream).path("payload");
  }

  private static List<String> readTextArray(JsonNode node) {
    List<String> values = new java.util.ArrayList<>();
    node.forEach(element -> values.add(element.asText()));
    return List.copyOf(values);
  }

  /** Deliberately self-referential value used to force a serializer failure. */
  private static final class SelfReferentialValue {
    @JsonProperty("self")
    Object self() {
      return this;
    }
  }
}
