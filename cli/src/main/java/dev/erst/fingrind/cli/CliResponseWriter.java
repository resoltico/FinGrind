package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.PostEntryResult;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/** Writes deterministic JSON envelopes for FinGrind CLI responses. */
final class CliResponseWriter {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PrintStream outputStream;

  CliResponseWriter(PrintStream outputStream) {
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
  }

  /** Writes one generic success envelope. */
  void writeSuccess(Map<String, ?> payload) {
    writeSuccess(payload, false);
  }

  /** Writes one generic success envelope, optionally pretty-printed for discovery surfaces. */
  void writeSuccess(Map<String, ?> payload, boolean pretty) {
    Map<String, Object> envelope = newEnvelope();
    envelope.put("status", "ok");
    envelope.put("payload", payload);
    writeEnvelope(envelope, pretty);
  }

  /** Writes one deterministic failure envelope. */
  void writeFailure(CliFailure failure) {
    Map<String, Object> envelope = newEnvelope();
    envelope.put("status", "error");
    envelope.put("code", failure.code());
    envelope.put("message", failure.message());
    if (failure.hint() != null) {
      envelope.put("hint", failure.hint());
    }
    if (failure.argument() != null) {
      envelope.put("argument", failure.argument());
    }
    writeEnvelope(envelope, false);
  }

  /** Writes one deterministic failure envelope. */
  void writeFailure(String code, String message) {
    writeFailure(new CliFailure(code, message, null, null));
  }

  /** Writes one entry write-boundary result as a deterministic JSON envelope. */
  void writePostEntryResult(PostEntryResult result) {
    Map<String, Object> envelope =
        switch (result) {
          case PostEntryResult.PreflightAccepted accepted -> preflightEnvelope(accepted);
          case PostEntryResult.Committed committed -> committedEnvelope(committed);
          case PostEntryResult.Rejected rejected -> rejectedEnvelope(rejected);
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one raw JSON document, optionally pretty-printed. */
  void writeJson(Object value, boolean pretty) {
    if (pretty) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, value);
    } else {
      objectMapper.writeValue(outputStream, value);
    }
    outputStream.println();
    outputStream.flush();
  }

  private Map<String, Object> preflightEnvelope(PostEntryResult.PreflightAccepted accepted) {
    Map<String, Object> envelope = newEnvelope();
    envelope.put("status", "preflight-accepted");
    envelope.put("idempotencyKey", accepted.idempotencyKey().value());
    envelope.put("effectiveDate", accepted.effectiveDate().toString());
    return envelope;
  }

  private Map<String, Object> committedEnvelope(PostEntryResult.Committed committed) {
    Map<String, Object> envelope = newEnvelope();
    envelope.put("status", "committed");
    envelope.put("postingId", committed.postingId().value());
    envelope.put("idempotencyKey", committed.idempotencyKey().value());
    envelope.put("effectiveDate", committed.effectiveDate().toString());
    envelope.put("recordedAt", committed.recordedAt().toString());
    return envelope;
  }

  private Map<String, Object> rejectedEnvelope(PostEntryResult.Rejected rejected) {
    Map<String, Object> envelope = newEnvelope();
    envelope.put("status", "rejected");
    envelope.put("code", rejected.code().name());
    envelope.put("message", rejected.message());
    envelope.put("idempotencyKey", rejected.idempotencyKey().value());
    return envelope;
  }

  private static Map<String, Object> newEnvelope() {
    return new LinkedHashMap<>();
  }

  private void writeEnvelope(Map<String, ?> envelope, boolean pretty) {
    writeJson(envelope, pretty);
  }
}
