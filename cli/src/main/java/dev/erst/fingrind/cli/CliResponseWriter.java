package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.BookAdministrationRejection;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.ListAccountsResult;
import dev.erst.fingrind.application.MachineContract;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingRejection;
import dev.erst.fingrind.sqlite.RekeyBookResult;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Writes deterministic JSON envelopes for FinGrind CLI responses. */
final class CliResponseWriter {
  private final ObjectMapper objectMapper = configuredObjectMapper();
  private final PrintStream outputStream;

  CliResponseWriter(PrintStream outputStream) {
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream");
  }

  /** Writes the canonical help descriptor as a success envelope. */
  void writeHelp(MachineContract.HelpDescriptor helpDescriptor) {
    writeSuccess(helpDescriptor, true);
  }

  /** Writes the canonical capabilities descriptor as a success envelope. */
  void writeCapabilities(MachineContract.CapabilitiesDescriptor capabilitiesDescriptor) {
    writeSuccess(capabilitiesDescriptor, true);
  }

  /** Writes the canonical version descriptor as a success envelope. */
  void writeVersion(MachineContract.VersionDescriptor versionDescriptor) {
    writeSuccess(versionDescriptor, true);
  }

  /** Writes the canonical request-template descriptor as raw JSON. */
  void writeRequestTemplate(MachineContract.PostingRequestTemplateDescriptor requestTemplate) {
    writeJson(requestTemplate, true);
  }

  /** Writes one deterministic failure envelope. */
  void writeFailure(CliFailure failure) {
    writeEnvelope(
        new FailureEnvelope(
            "error", failure.code(), failure.message(), failure.hint(), failure.argument()),
        false);
  }

  /** Writes one deterministic failure envelope. */
  void writeFailure(String code, String message) {
    writeFailure(new CliFailure(code, message, null, null));
  }

  /** Writes one entry write-boundary result as a deterministic JSON envelope. */
  void writePostEntryResult(PostEntryResult result) {
    Object envelope =
        switch (result) {
          case PostEntryResult.PreflightAccepted accepted -> preflightEnvelope(accepted);
          case PostEntryResult.Committed committed -> committedEnvelope(committed);
          case PostEntryResult.Rejected rejected -> rejectedEnvelope(rejected);
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one explicit open-book result as a deterministic JSON envelope. */
  void writeOpenBookResult(Path bookFilePath, OpenBookResult result) {
    Object envelope =
        switch (result) {
          case OpenBookResult.Opened opened ->
              successEnvelope(
                  new OpenBookPayload(
                      absolutePath(bookFilePath), opened.initializedAt().toString()));
          case OpenBookResult.Rejected rejected ->
              administrationRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one explicit rekey-book result as a deterministic JSON envelope. */
  void writeRekeyBookResult(RekeyBookResult result) {
    Object envelope =
        switch (result) {
          case RekeyBookResult.Rekeyed rekeyed ->
              successEnvelope(new RekeyBookPayload(absolutePath(rekeyed.bookFilePath())));
          case RekeyBookResult.Rejected rejected ->
              administrationRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one account-declaration result as a deterministic JSON envelope. */
  void writeDeclareAccountResult(DeclareAccountResult result) {
    Object envelope =
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
    Object envelope =
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
    JsonNode node = withoutNulls(objectMapper.valueToTree(value));
    if (pretty) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, node);
    } else {
      objectMapper.writeValue(outputStream, node);
    }
    outputStream.println();
    outputStream.flush();
  }

  private void writeSuccess(Object payload, boolean pretty) {
    writeEnvelope(successEnvelope(payload), pretty);
  }

  private PreflightAcceptedEnvelope preflightEnvelope(PostEntryResult.PreflightAccepted accepted) {
    return new PreflightAcceptedEnvelope(
        "preflight-accepted",
        accepted.idempotencyKey().value(),
        accepted.effectiveDate().toString());
  }

  private CommittedEnvelope committedEnvelope(PostEntryResult.Committed committed) {
    return new CommittedEnvelope(
        "committed",
        committed.postingId().value(),
        committed.idempotencyKey().value(),
        committed.effectiveDate().toString(),
        committed.recordedAt().toString());
  }

  private RejectedEnvelope rejectedEnvelope(PostEntryResult.Rejected rejected) {
    return new RejectedEnvelope(
        "rejected",
        PostingRejection.wireCode(rejected.rejection()),
        postingRejectionMessage(rejected.rejection()),
        rejected.idempotencyKey().value(),
        postingRejectionDetails(rejected.rejection()).orElse(null));
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

  private static Optional<Map<String, Object>> postingRejectionDetails(PostingRejection rejection) {
    if (rejection instanceof PostingRejection.UnknownAccount unknownAccount) {
      return Optional.of(Map.of("accountCode", unknownAccount.accountCode().value()));
    }
    if (rejection instanceof PostingRejection.InactiveAccount inactiveAccount) {
      return Optional.of(Map.of("accountCode", inactiveAccount.accountCode().value()));
    }
    if (rejection instanceof PostingRejection.ReversalTargetNotFound reversalTargetNotFound) {
      return Optional.of(Map.of("priorPostingId", reversalTargetNotFound.priorPostingId().value()));
    }
    if (rejection instanceof PostingRejection.ReversalAlreadyExists reversalAlreadyExists) {
      return Optional.of(Map.of("priorPostingId", reversalAlreadyExists.priorPostingId().value()));
    }
    if (rejection
        instanceof PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget) {
      return Optional.of(
          Map.of("priorPostingId", reversalDoesNotNegateTarget.priorPostingId().value()));
    }
    return Optional.empty();
  }

  private static RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return new RejectedEnvelope(
        "rejected",
        BookAdministrationRejection.wireCode(rejection),
        administrationRejectionMessage(rejection),
        null,
        administrationRejectionDetails(rejection).orElse(null));
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

  private static Optional<Map<String, Object>> administrationRejectionDetails(
      BookAdministrationRejection rejection) {
    if (rejection instanceof BookAdministrationRejection.NormalBalanceConflict conflict) {
      return Optional.of(
          Map.of(
              "accountCode",
              conflict.accountCode().value(),
              "existingNormalBalance",
              conflict.existingNormalBalance().name(),
              "requestedNormalBalance",
              conflict.requestedNormalBalance().name()));
    }
    return Optional.empty();
  }

  private static DeclaredAccountPayload accountPayload(DeclaredAccount account) {
    return new DeclaredAccountPayload(
        account.accountCode().value(),
        account.accountName().value(),
        account.normalBalance().name(),
        account.active(),
        account.declaredAt().toString());
  }

  private static SuccessEnvelope successEnvelope(Object payload) {
    return new SuccessEnvelope("ok", payload);
  }

  private static String absolutePath(Path bookFilePath) {
    return bookFilePath.toAbsolutePath().normalize().toString();
  }

  private void writeEnvelope(Object envelope, boolean pretty) {
    writeJson(envelope, pretty);
  }

  private static ObjectMapper configuredObjectMapper() {
    return new ObjectMapper();
  }

  private static JsonNode withoutNulls(JsonNode node) {
    if (node.isNull()) {
      return node;
    }
    if (node.isObject()) {
      ObjectNode objectNode = (ObjectNode) node;
      List<String> nullFields = new ArrayList<>();
      objectNode
          .propertyStream()
          .forEach(
              entry -> {
                JsonNode value = entry.getValue();
                if (value.isNull()) {
                  nullFields.add(entry.getKey());
                } else {
                  objectNode.set(entry.getKey(), withoutNulls(value));
                }
              });
      objectNode.remove(nullFields);
      return objectNode;
    }
    if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;
      for (int index = 0; index < arrayNode.size(); index++) {
        JsonNode value = arrayNode.get(index);
        if (!value.isNull()) {
          arrayNode.set(index, withoutNulls(value));
        }
      }
    }
    return node;
  }

  private record SuccessEnvelope(String status, Object payload) {}

  private record FailureEnvelope(
      String status, String code, String message, String hint, String argument) {}

  private record PreflightAcceptedEnvelope(
      String status, String idempotencyKey, String effectiveDate) {}

  private record CommittedEnvelope(
      String status,
      String postingId,
      String idempotencyKey,
      String effectiveDate,
      String recordedAt) {}

  private record RejectedEnvelope(
      String status, String code, String message, String idempotencyKey, Object details) {}

  private record OpenBookPayload(String bookFile, String initializedAt) {}

  private record RekeyBookPayload(String bookFile) {}

  private record DeclaredAccountPayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt) {}
}
