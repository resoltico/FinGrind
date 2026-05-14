package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliResponseWriter}. */
class CliAdministrativeCommandResponseWriterTest extends CliResponseWriterTestSupport {
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
        openedBookResult(Instant.parse("2026-04-17T10:15:30Z")),
        OutputMode.HUMAN);
    String openBookHuman = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(openBookHuman.contains("Book Initialized"));
    assertTrue(openBookHuman.contains("Entity name"));
    assertTrue(openBookHuman.contains("Acme Studio"));
    assertTrue(openBookHuman.contains("Functional currency"));
    assertTrue(openBookHuman.contains("Fiscal year start"));
    outputStream.reset();
    responseWriter.writeRekeyBookResult(
        new RekeyBookResult.Rekeyed(Path.of("books/book.sqlite")), OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Book Rekeyed"));
    outputStream.reset();
    responseWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Declared(
            CliIoFixtureSupport.declaredAccount(
                "1000",
                "Cash",
                dev.erst.fingrind.core.AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-17T10:15:30Z"))),
        OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Account Declared"));
    outputStream.reset();
    responseWriter.writeClosePeriodResult(
        new ClosePeriodResult.Closed(CliFixtureSupport.sampleClosedPeriod()), OutputMode.HUMAN);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Period Closed"));
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
                openedBookResult(Instant.parse("2026-04-17T10:15:30Z")),
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
                    CliIoFixtureSupport.declaredAccount(
                        "1000",
                        "Cash",
                        dev.erst.fingrind.core.AccountType.ASSET,
                        NormalBalance.DEBIT,
                        true,
                        Instant.parse("2026-04-17T10:15:30Z"))),
                OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            responseWriter.writeClosePeriodResult(
                new ClosePeriodResult.Closed(CliFixtureSupport.sampleClosedPeriod()),
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
    outputStream.reset();
    responseWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.RetainedEarningsAccountMissing(
                new AccountCode("3200"))),
        OutputMode.HUMAN);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("retained-earnings-account-missing"));
  }

  @Test
  void writeOpenBookResult_writesSuccessEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writeOpenBookResult(
        Path.of("book.sqlite"), openedBookResult(Instant.parse("2026-04-07T10:15:30Z")));
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"status\":\"ok\""));
    assertTrue(json.contains("\"bookFile\""));
    assertTrue(json.contains("\"initializedAt\":\"2026-04-07T10:15:30Z\""));
    assertTrue(json.contains("\"bookIdentity\""));
    assertTrue(json.contains("\"entityName\":\"Acme Studio\""));
    assertTrue(json.contains("\"functionalCurrency\":\"EUR\""));
    assertTrue(json.contains("\"fiscalYearStart\":\"01-01\""));
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
        CliIoFixtureSupport.declaredAccount(
            "1000",
            "Cash",
            dev.erst.fingrind.core.AccountType.ASSET,
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
            new AccountPage(java.util.List.of(declaredAccount), 50, java.util.Optional.empty())));
    String declareSuccessJson = declareSuccessOutput.toString(StandardCharsets.UTF_8);
    assertTrue(declareSuccessJson.contains("\"accountName\":\"Cash\""));
    assertTrue(declareSuccessJson.contains("\"declaredAt\":\"2026-04-07T10:15:30Z\""));
    String listSuccessJson = listSuccessOutput.toString(StandardCharsets.UTF_8);
    assertTrue(listSuccessJson.contains("\"limit\":50"));
    assertFalse(listSuccessJson.contains("\"nextCursor\""));
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
            new BookAdministrationRejection.AccountRoleConflict(
                new AccountCode("1000"),
                dev.erst.fingrind.core.AccountRole.ORDINARY,
                dev.erst.fingrind.core.AccountRole.CONTRA)));
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
    assertTrue(declareConflictJson.contains("\"code\":\"account-role-conflict\""));
    assertTrue(declareConflictJson.contains("\"accountCode\":\"1000\""));
    assertTrue(declareConflictJson.contains("\"existingAccountRole\":\"ORDINARY\""));
    assertTrue(declareConflictJson.contains("\"requestedAccountRole\":\"CONTRA\""));
    assertTrue(declareConflictJson.contains("already exists with account role"));
    assertTrue(
        listRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"query-book-not-initialized\""));
  }

  @Test
  void writeClosePeriodResult_writesSuccessAndDetailedRejectionEnvelopes() {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));
    successWriter.writeClosePeriodResult(
        new ClosePeriodResult.Closed(CliFixtureSupport.sampleClosedPeriod()), OutputMode.JSON);
    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertTrue(successJson.contains("\"status\":\"ok\""));
    assertTrue(successJson.contains("\"closeOrder\":1"));
    assertTrue(successJson.contains("\"closingPostingIds\":[\"posting-close-1\"]"));

    ByteArrayOutputStream missingOutput = new ByteArrayOutputStream();
    CliResponseWriter missingWriter = new CliResponseWriter(utf8PrintStream(missingOutput));
    missingWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.RetainedEarningsAccountMissing(
                new AccountCode("3200"))),
        OutputMode.JSON);
    assertTrue(
        missingOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"retained-earnings-account-missing\""));

    ByteArrayOutputStream horizonOutput = new ByteArrayOutputStream();
    CliResponseWriter horizonWriter = new CliResponseWriter(utf8PrintStream(horizonOutput));
    horizonWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.PeriodCloseMustStartAt(LocalDate.parse("2026-04-01"))),
        OutputMode.JSON);
    String horizonJson = horizonOutput.toString(StandardCharsets.UTF_8);
    assertTrue(horizonJson.contains("\"code\":\"period-close-must-start-at\""));
    assertTrue(horizonJson.contains("\"requiredEffectiveDateFrom\":\"2026-04-01\""));

    ByteArrayOutputStream typeConflictOutput = new ByteArrayOutputStream();
    CliResponseWriter typeConflictWriter =
        new CliResponseWriter(utf8PrintStream(typeConflictOutput));
    typeConflictWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.AccountTypeConflict(
                new AccountCode("3200"),
                dev.erst.fingrind.core.AccountType.EQUITY,
                dev.erst.fingrind.core.AccountType.LIABILITY)),
        OutputMode.JSON);
    String typeConflictJson = typeConflictOutput.toString(StandardCharsets.UTF_8);
    assertTrue(typeConflictJson.contains("\"existingAccountType\":\"EQUITY\""));
    assertTrue(typeConflictJson.contains("\"requestedAccountType\":\"LIABILITY\""));

    ByteArrayOutputStream inactiveOutput = new ByteArrayOutputStream();
    CliResponseWriter inactiveWriter = new CliResponseWriter(utf8PrintStream(inactiveOutput));
    inactiveWriter.writeClosePeriodResult(
        new ClosePeriodResult.Rejected(
            new BookAdministrationRejection.RetainedEarningsAccountInactive(
                new AccountCode("3200"))),
        OutputMode.JSON);
    assertTrue(
        inactiveOutput.toString(StandardCharsets.UTF_8).contains("\"accountCode\":\"3200\""));
    assertEquals(
        2,
        CliExecutionPolicy.exitCodeFor(
            new ClosePeriodResult.Rejected(
                new BookAdministrationRejection.RetainedEarningsAccountMissing(
                    new AccountCode("3200")))));
  }

  @Test
  void writePostingRejections_includesStructuredCloseAndReversalDetails() {
    String closedPeriodJson =
        rejectedJson(
            new PostingRejection.ClosedPeriodViolation(
                LocalDate.parse("2026-04-30"), LocalDate.parse("2026-05-01")));
    assertTrue(closedPeriodJson.contains("\"closedThroughEffectiveDate\":\"2026-04-30\""));
    assertTrue(closedPeriodJson.contains("\"attemptedEffectiveDate\":\"2026-05-01\""));

    String retainedEarningsJson =
        rejectedJson(new PostingRejection.RetainedEarningsAccountReserved(new AccountCode("3200")));
    assertTrue(retainedEarningsJson.contains("\"accountCode\":\"3200\""));

    String missingPriorPostingJson =
        rejectedJson(new PostingRejection.ReversalTargetNotFound(new PostingId("posting-9")));
    assertTrue(missingPriorPostingJson.contains("\"priorPostingId\":\"posting-9\""));

    String existingReversalJson =
        rejectedJson(new PostingRejection.ReversalAlreadyExists(new PostingId("posting-8")));
    assertTrue(existingReversalJson.contains("\"priorPostingId\":\"posting-8\""));

    String negateTargetJson =
        rejectedJson(new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-7")));
    assertTrue(negateTargetJson.contains("\"priorPostingId\":\"posting-7\""));
  }
}
