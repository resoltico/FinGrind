package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingRejection;
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
    envelope.put("code", rejectionCode(rejected.rejection()));
    envelope.put("message", rejectionMessage(rejected.rejection()));
    envelope.put("idempotencyKey", rejected.idempotencyKey().value());
    Map<String, Object> details = rejectionDetails(rejected.rejection());
    if (!details.isEmpty()) {
      envelope.put("details", details);
    }
    return envelope;
  }

  private static String rejectionCode(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.DuplicateIdempotencyKey _ -> "duplicate-idempotency-key";
      case PostingRejection.CorrectionReasonRequired _ -> "correction-reason-required";
      case PostingRejection.CorrectionReasonForbidden _ -> "correction-reason-forbidden";
      case PostingRejection.CorrectionTargetNotFound _ -> "correction-target-not-found";
      case PostingRejection.ReversalAlreadyExists _ -> "reversal-already-exists";
      case PostingRejection.ReversalDoesNotNegateTarget _ -> "reversal-does-not-negate-target";
    };
  }

  private static String rejectionMessage(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.DuplicateIdempotencyKey _ ->
          "A posting with the same idempotency key already exists in this book.";
      case PostingRejection.CorrectionReasonRequired _ ->
          "Corrective postings must include a human-readable reason.";
      case PostingRejection.CorrectionReasonForbidden _ ->
          "A corrective reason is only permitted when a correction target is present.";
      case PostingRejection.CorrectionTargetNotFound correctionTargetNotFound ->
          "No committed posting exists for correction target '%s'."
              .formatted(correctionTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          "Posting '%s' already has a full reversal."
              .formatted(reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          "Reversal candidate does not negate posting '%s'."
              .formatted(reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  private static Map<String, Object> rejectionDetails(PostingRejection rejection) {
    if (rejection instanceof PostingRejection.CorrectionTargetNotFound correctionTargetNotFound) {
      return Map.of("priorPostingId", correctionTargetNotFound.priorPostingId().value());
    }
    if (rejection instanceof PostingRejection.ReversalAlreadyExists reversalAlreadyExists) {
      return Map.of("priorPostingId", reversalAlreadyExists.priorPostingId().value());
    }
    if (rejection
        instanceof PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget) {
      return Map.of("priorPostingId", reversalDoesNotNegateTarget.priorPostingId().value());
    }
    return Map.of();
  }

  private static Map<String, Object> newEnvelope() {
    return new LinkedHashMap<>();
  }

  private void writeEnvelope(Map<String, ?> envelope, boolean pretty) {
    writeJson(envelope, pretty);
  }
}
