package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingRejection;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
            new PostingRejection.CorrectionTargetNotFound(new PostingId("posting-1"))));

    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"status\":\"rejected\""));
    assertTrue(json.contains("\"code\":\"correction-target-not-found\""));
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
  void writePostEntryResult_writesCorrectionReasonRequiredRejection() {
    String json = rejectedJson(new PostingRejection.CorrectionReasonRequired());

    assertTrue(json.contains("\"code\":\"correction-reason-required\""));
    assertTrue(json.contains("must include a human-readable reason"));
  }

  @Test
  void writePostEntryResult_writesCorrectionReasonForbiddenRejection() {
    String json = rejectedJson(new PostingRejection.CorrectionReasonForbidden());

    assertTrue(json.contains("\"code\":\"correction-reason-forbidden\""));
    assertTrue(json.contains("only permitted when a correction target is present"));
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
}
