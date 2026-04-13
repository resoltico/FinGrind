package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.application.BookAdministrationRejection;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.ListAccountsResult;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliResponseWriter}. */
class CliResponseWriterTest {
  @Test
  void writeSuccess_writesOkEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeSuccess(Map.of("command", "help"));

    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
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
    String unknownJson = rejectedJson(new PostingRejection.UnknownAccount(new AccountCode("1000")));
    String inactiveJson =
        rejectedJson(new PostingRejection.InactiveAccount(new AccountCode("1000")));

    assertTrue(bookJson.contains("\"code\":\"book-not-initialized\""));
    assertTrue(unknownJson.contains("\"accountCode\":\"1000\""));
    assertTrue(inactiveJson.contains("\"code\":\"inactive-account\""));
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
  void writeDeclareAccountAndListAccountsResults_writeSuccessAndRejectionEnvelopes() {
    ByteArrayOutputStream successOutput = new ByteArrayOutputStream();
    CliResponseWriter successWriter = new CliResponseWriter(utf8PrintStream(successOutput));
    DeclaredAccount declaredAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    successWriter.writeDeclareAccountResult(new DeclareAccountResult.Declared(declaredAccount));
    successWriter.writeListAccountsResult(
        new ListAccountsResult.Listed(java.util.List.of(declaredAccount)));

    String successJson = successOutput.toString(StandardCharsets.UTF_8);
    assertTrue(successJson.contains("\"accountName\":\"Cash\""));
    assertTrue(successJson.contains("\"declaredAt\":\"2026-04-07T10:15:30Z\""));

    ByteArrayOutputStream declareRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter declareRejectionWriter =
        new CliResponseWriter(utf8PrintStream(declareRejectionOutput));
    declareRejectionWriter.writeDeclareAccountResult(
        new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()));

    ByteArrayOutputStream listRejectionOutput = new ByteArrayOutputStream();
    CliResponseWriter listRejectionWriter =
        new CliResponseWriter(utf8PrintStream(listRejectionOutput));
    listRejectionWriter.writeListAccountsResult(
        new ListAccountsResult.Rejected(
            new BookAdministrationRejection.NormalBalanceConflict(
                new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT)));

    assertTrue(
        declareRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"book-not-initialized\""));
    assertTrue(
        listRejectionOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"account-normal-balance-conflict\""));
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
}
