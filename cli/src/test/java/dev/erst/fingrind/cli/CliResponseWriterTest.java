package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingRejectionCode;
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
  void writePostEntryResult_writesRejectedEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writePostEntryResult(
        new PostEntryResult.Rejected(
            PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY,
            "duplicate",
            new IdempotencyKey("idem-1")));

    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"rejected\""));
  }

  private static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }
}
