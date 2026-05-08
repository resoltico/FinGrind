package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliResponseWriter}. */
class CliPostEntryResponseWriterTest extends CliResponseWriterTestSupport {
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
}
