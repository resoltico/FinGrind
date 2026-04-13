package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.BookAdministrationRejection;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.ListAccountsResult;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingRejection;
import java.io.PrintStream;
import java.nio.file.Path;
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

  /** Writes one explicit open-book result as a deterministic JSON envelope. */
  void writeOpenBookResult(Path bookFilePath, OpenBookResult result) {
    Map<String, Object> envelope =
        switch (result) {
          case OpenBookResult.Opened opened -> {
            Map<String, Object> payload = newEnvelope();
            payload.put("bookFile", absolutePath(bookFilePath));
            payload.put("initializedAt", opened.initializedAt().toString());
            yield successEnvelope(payload);
          }
          case OpenBookResult.Rejected rejected ->
              administrationRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one account-declaration result as a deterministic JSON envelope. */
  void writeDeclareAccountResult(DeclareAccountResult result) {
    Map<String, Object> envelope =
        switch (result) {
          case DeclareAccountResult.Declared declared ->
              successEnvelope(accountPayload(declared.account()));
          case DeclareAccountResult.Rejected rejected ->
              administrationRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one account-listing result as a deterministic JSON envelope. */
  void writeListAccountsResult(ListAccountsResult result) {
    Map<String, Object> envelope =
        switch (result) {
          case ListAccountsResult.Listed listed ->
              successEnvelope(
                  listed.accounts().stream().map(CliResponseWriter::accountPayload).toList());
          case ListAccountsResult.Rejected rejected ->
              administrationRejectedEnvelope(rejected.rejection());
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
    envelope.put("code", postingRejectionCode(rejected.rejection()));
    envelope.put("message", postingRejectionMessage(rejected.rejection()));
    envelope.put("idempotencyKey", rejected.idempotencyKey().value());
    Map<String, Object> details = postingRejectionDetails(rejected.rejection());
    if (!details.isEmpty()) {
      envelope.put("details", details);
    }
    return envelope;
  }

  private static String postingRejectionCode(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> "book-not-initialized";
      case PostingRejection.UnknownAccount _ -> "unknown-account";
      case PostingRejection.InactiveAccount _ -> "inactive-account";
      case PostingRejection.DuplicateIdempotencyKey _ -> "duplicate-idempotency-key";
      case PostingRejection.ReversalReasonRequired _ -> "reversal-reason-required";
      case PostingRejection.ReversalReasonForbidden _ -> "reversal-reason-forbidden";
      case PostingRejection.ReversalTargetNotFound _ -> "reversal-target-not-found";
      case PostingRejection.ReversalAlreadyExists _ -> "reversal-already-exists";
      case PostingRejection.ReversalDoesNotNegateTarget _ -> "reversal-does-not-negate-target";
    };
  }

  private static String postingRejectionMessage(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with open-book.";
      case PostingRejection.UnknownAccount unknownAccount ->
          "Account '%s' is not declared in this book."
              .formatted(unknownAccount.accountCode().value());
      case PostingRejection.InactiveAccount inactiveAccount ->
          "Account '%s' is inactive in this book.".formatted(inactiveAccount.accountCode().value());
      case PostingRejection.DuplicateIdempotencyKey _ ->
          "A posting with the same idempotency key already exists in this book.";
      case PostingRejection.ReversalReasonRequired _ ->
          "Reversal postings must include a human-readable reason.";
      case PostingRejection.ReversalReasonForbidden _ ->
          "A reversal reason is only permitted when a reversal target is present.";
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          "No committed posting exists for reversal target '%s'."
              .formatted(reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          "Posting '%s' already has a full reversal."
              .formatted(reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          "Reversal candidate does not negate posting '%s'."
              .formatted(reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  private static Map<String, Object> postingRejectionDetails(PostingRejection rejection) {
    if (rejection instanceof PostingRejection.UnknownAccount unknownAccount) {
      return Map.of("accountCode", unknownAccount.accountCode().value());
    }
    if (rejection instanceof PostingRejection.InactiveAccount inactiveAccount) {
      return Map.of("accountCode", inactiveAccount.accountCode().value());
    }
    if (rejection instanceof PostingRejection.ReversalTargetNotFound reversalTargetNotFound) {
      return Map.of("priorPostingId", reversalTargetNotFound.priorPostingId().value());
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

  private static Map<String, Object> administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    Map<String, Object> envelope = newEnvelope();
    envelope.put("status", "rejected");
    envelope.put("code", administrationRejectionCode(rejection));
    envelope.put("message", administrationRejectionMessage(rejection));
    Map<String, Object> details = administrationRejectionDetails(rejection);
    if (!details.isEmpty()) {
      envelope.put("details", details);
    }
    return envelope;
  }

  private static String administrationRejectionCode(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ -> "book-already-initialized";
      case BookAdministrationRejection.BookNotInitialized _ -> "book-not-initialized";
      case BookAdministrationRejection.BookContainsSchema _ -> "book-contains-schema";
      case BookAdministrationRejection.NormalBalanceConflict _ -> "account-normal-balance-conflict";
    };
  }

  private static String administrationRejectionMessage(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ ->
          "The selected book is already initialized.";
      case BookAdministrationRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with open-book.";
      case BookAdministrationRejection.BookContainsSchema _ ->
          "The selected SQLite file already contains schema objects and cannot be initialized as a new book.";
      case BookAdministrationRejection.NormalBalanceConflict normalBalanceConflict ->
          "Account '%s' already exists with normal balance '%s'; FinGrind will not amend it to '%s'."
              .formatted(
                  normalBalanceConflict.accountCode().value(),
                  normalBalanceConflict.existingNormalBalance().name(),
                  normalBalanceConflict.requestedNormalBalance().name());
    };
  }

  private static Map<String, Object> administrationRejectionDetails(
      BookAdministrationRejection rejection) {
    if (rejection instanceof BookAdministrationRejection.NormalBalanceConflict conflict) {
      return Map.of(
          "accountCode",
          conflict.accountCode().value(),
          "existingNormalBalance",
          conflict.existingNormalBalance().name(),
          "requestedNormalBalance",
          conflict.requestedNormalBalance().name());
    }
    return Map.of();
  }

  private static Map<String, Object> accountPayload(DeclaredAccount account) {
    Map<String, Object> payload = newEnvelope();
    payload.put("accountCode", account.accountCode().value());
    payload.put("accountName", account.accountName().value());
    payload.put("normalBalance", account.normalBalance().name());
    payload.put("active", account.active());
    payload.put("declaredAt", account.declaredAt().toString());
    return payload;
  }

  private static Map<String, Object> successEnvelope(Object payload) {
    Map<String, Object> envelope = newEnvelope();
    envelope.put("status", "ok");
    envelope.put("payload", payload);
    return envelope;
  }

  private static String absolutePath(Path bookFilePath) {
    return bookFilePath.toAbsolutePath().normalize().toString();
  }

  private static Map<String, Object> newEnvelope() {
    return new LinkedHashMap<>();
  }

  private void writeEnvelope(Map<String, ?> envelope, boolean pretty) {
    writeJson(envelope, pretty);
  }
}
