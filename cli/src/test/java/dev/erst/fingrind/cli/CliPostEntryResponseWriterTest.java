package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link CliResponseWriter}. */
class CliPostEntryResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writePostEntryResult_writesPreflightEnvelope() throws java.io.IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writePostEntryResult(
        CliPostEntryResultFixtures.preflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")));
    assertJsonContains(outputStream, "\"status\":\"ok\"");
    assertJsonContains(outputStream, "\"resolvedJournal\"");
    assertJsonContains(outputStream, "\"eventClass\":\"SETTLED_SALE\"");
    assertJsonContains(outputStream, "\"containedTypedEvents\":[\"SETTLED_SALE\"]");
    assertExpandedLines(readJson(outputStream));
  }

  @Test
  void writePostEntryResult_writesCommittedEnvelope() throws java.io.IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writePostEntryResult(
        CliPostEntryResultFixtures.committed(
            new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            false));
    assertJsonContains(outputStream, "\"status\":\"ok\"");
    assertJsonContains(outputStream, "\"resolvedJournal\"");
    assertJsonContains(outputStream, "\"eventClass\":\"SETTLED_SALE\"");
    assertJsonContains(outputStream, "\"accountCode\":\"1000\"");
    assertExpandedLines(readJson(outputStream));
  }

  @Test
  void writePostEntryResult_writesRejectedEnvelopeWithStructuredDetails() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    responseWriter.writePostEntryResult(
        new PostEntryResult.CommitRejected(
            new IdempotencyKey("idem-1"),
            new PostingRejection.ReversalTargetNotFound(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertJsonContains(json, "\"status\":\"rejected\"");
    assertJsonContains(json, "\"code\":\"reversal-target-not-found\"");
    assertJsonContains(json, "\"priorPostingId\":\"posting-1\"");
  }

  @Test
  void writePostEntryResult_writesDuplicateIdempotencyRejectionWithoutDetails() {
    String json = rejectedJson(new PostingRejection.IdempotencyKeyConflict());
    assertJsonContains(json, "\"code\":\"idempotency-key-conflict\"");
    assertTrue(json.contains("different committed posting request"));
    assertFalse(json.contains("\"details\""));
  }

  @Test
  void writePostEntryResult_writesReversalAlreadyExistsRejection() {
    String json =
        rejectedJson(new PostingRejection.ReversalAlreadyExists(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
    assertJsonContains(json, "\"code\":\"reversal-already-exists\"");
    assertTrue(json.contains("already has a full reversal"));
    assertJsonContains(json, "\"priorPostingId\":\"posting-1\"");
  }

  @Test
  void writePostEntryResult_writesReversalTargetIsReversalRejection() {
    String json =
        rejectedJson(
            new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
    assertJsonContains(json, "\"code\":\"reversal-target-is-reversal\"");
    assertTrue(json.contains("cannot be reversed"));
    assertJsonContains(json, "\"priorPostingId\":\"posting-1\"");
  }

  @Test
  void writePostEntryResult_writesReversalDoesNotNegateTargetRejection() {
    String json =
        rejectedJson(new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
    assertJsonContains(json, "\"code\":\"reversal-does-not-negate-target\"");
    assertTrue(json.contains("does not negate posting"));
    assertJsonContains(json, "\"priorPostingId\":\"posting-1\"");
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
    assertJsonContains(bookJson, "\"code\":\"posting-book-not-initialized\"");
    assertJsonContains(accountStateJson, "\"code\":\"account-state-violations\"");
    assertJsonContains(accountStateJson, "\"accountCode\":\"1000\"");
    assertJsonContains(accountStateJson, "\"code\":\"inactive-account\"");
  }

  private static void assertExpandedLines(JsonNode envelope) {
    JsonNode lines =
        envelope.path("payload").path("resolvedJournal").path("expandedLines").path("lines");
    assertEquals(3, lines.size(), envelope.toPrettyString());
    assertEquals("1000", lines.get(0).path("accountCode").stringValue(), envelope.toPrettyString());
    assertEquals("4000", lines.get(1).path("accountCode").stringValue(), envelope.toPrettyString());
    assertEquals("2100", lines.get(2).path("accountCode").stringValue(), envelope.toPrettyString());
  }
}
