package dev.erst.fingrind.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.contract.*;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Writes deterministic JSON envelopes for FinGrind CLI responses. */
final class CliResponseWriter {
  private static final String OPEN_BOOK_OPERATION =
      dev.erst.fingrind.contract.protocol.ProtocolCatalog.operationName(
          dev.erst.fingrind.contract.protocol.OperationId.OPEN_BOOK);

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

  /** Writes the canonical ledger-plan template descriptor as raw JSON. */
  void writePlanTemplate(MachineContract.LedgerPlanTemplateDescriptor planTemplate) {
    writeJson(planTemplate, true);
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
          case PostEntryResult.Rejected rejected -> postingRejectedEnvelope(rejected);
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

  /** Writes one generated book-key-file result as a deterministic JSON envelope. */
  void writeGenerateBookKeyFileResult(
      SqliteBookKeyFileGenerator.GeneratedKeyFile generatedKeyFile) {
    writeEnvelope(
        successEnvelope(
            new GeneratedBookKeyFilePayload(
                absolutePath(generatedKeyFile.bookKeyFilePath()),
                generatedKeyFile.encoding(),
                generatedKeyFile.entropyBits(),
                generatedKeyFile.permissions())),
        false);
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

  /** Writes one book-inspection snapshot as a deterministic JSON envelope. */
  void writeBookInspection(Path bookFilePath, BookInspection inspection) {
    writeEnvelope(
        successEnvelope(
            new BookInspectionPayload(
                absolutePath(bookFilePath),
                inspection.status().name(),
                inspection.initialized(),
                inspection.compatibleWithCurrentBinary(),
                inspection.canInitializeWithOpenBook(),
                inspection.applicationId(),
                inspection.detectedBookFormatVersion(),
                inspection.supportedBookFormatVersion(),
                inspection.migrationPolicy(),
                stringOrNull(inspection.initializedAt()))),
        false);
  }

  /** Writes one account-listing result as a deterministic JSON envelope. */
  void writeListAccountsResult(ListAccountsResult result) {
    Object envelope =
        switch (result) {
          case ListAccountsResult.Listed listed ->
              successEnvelope(accountPagePayload(listed.page()));
          case ListAccountsResult.Rejected rejected -> queryRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one committed-posting lookup result as a deterministic JSON envelope. */
  void writeGetPostingResult(GetPostingResult result) {
    Object envelope =
        switch (result) {
          case GetPostingResult.Found found -> successEnvelope(postingPayload(found.postingFact()));
          case GetPostingResult.Rejected rejected -> queryRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one committed-posting page result as a deterministic JSON envelope. */
  void writeListPostingsResult(ListPostingsResult result) {
    Object envelope =
        switch (result) {
          case ListPostingsResult.Listed listed ->
              successEnvelope(postingPagePayload(listed.page()));
          case ListPostingsResult.Rejected rejected -> queryRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one account-balance result as a deterministic JSON envelope. */
  void writeAccountBalanceResult(AccountBalanceResult result) {
    Object envelope =
        switch (result) {
          case AccountBalanceResult.Reported reported ->
              successEnvelope(accountBalancePayload(reported.snapshot()));
          case AccountBalanceResult.Rejected rejected ->
              queryRejectedEnvelope(rejected.rejection());
        };
    writeEnvelope(envelope, false);
  }

  /** Writes one ledger-plan execution result as a deterministic JSON envelope. */
  void writeLedgerPlanResult(LedgerPlanResult result) {
    if (result.status() == dev.erst.fingrind.contract.LedgerPlanStatus.SUCCEEDED) {
      writeEnvelope(new SuccessEnvelope("committed", result), false);
      return;
    }
    LedgerJournalEntry failedStep = result.journal().steps().getLast();
    dev.erst.fingrind.contract.LedgerStepFailure failure = failedStep.failure().orElseThrow();
    writeEnvelope(
        new RejectedEnvelope(
            "rejected", failure.code(), failure.message(), null, Map.of("plan", result)),
        false);
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

  private RejectedEnvelope postingRejectedEnvelope(PostEntryResult.Rejected rejected) {
    return new RejectedEnvelope(
        "rejected",
        PostingRejection.wireCode(rejected.rejection()),
        postingRejectionMessage(rejected.rejection()),
        rejected.idempotencyKey().value(),
        postingRejectionDetails(rejected.rejection()));
  }

  private static String postingRejectionMessage(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + OPEN_BOOK_OPERATION
              + ".";
      case PostingRejection.AccountStateViolations violations ->
          "Posting references undeclared or inactive accounts."
              + " Fix every issue in details.violations before retrying."
              + " Reported issues: "
              + violations.violations().size();
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

  private static @Nullable Object postingRejectionDetails(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> null;
      case PostingRejection.AccountStateViolations violations ->
          Map.of(
              "violations",
              violations.violations().stream()
                  .map(CliResponseWriter::accountStateViolationPayload)
                  .toList());
      case PostingRejection.DuplicateIdempotencyKey _ -> null;
      case PostingRejection.ReversalReasonRequired _ -> null;
      case PostingRejection.ReversalReasonForbidden _ -> null;
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          Map.of("priorPostingId", reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          Map.of("priorPostingId", reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          Map.of("priorPostingId", reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  private static Map<String, Object> accountStateViolationPayload(
      PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount unknownAccount ->
          Map.of("code", "unknown-account", "accountCode", unknownAccount.accountCode().value());
      case PostingRejection.InactiveAccount inactiveAccount ->
          Map.of("code", "inactive-account", "accountCode", inactiveAccount.accountCode().value());
    };
  }

  private static RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return new RejectedEnvelope(
        "rejected",
        BookAdministrationRejection.wireCode(rejection),
        administrationRejectionMessage(rejection),
        null,
        administrationRejectionDetails(rejection));
  }

  private static String administrationRejectionMessage(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ ->
          "The selected book is already initialized.";
      case BookAdministrationRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + OPEN_BOOK_OPERATION
              + ".";
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

  private static @Nullable Object administrationRejectionDetails(
      BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ -> null;
      case BookAdministrationRejection.BookNotInitialized _ -> null;
      case BookAdministrationRejection.BookContainsSchema _ -> null;
      case BookAdministrationRejection.NormalBalanceConflict conflict ->
          Map.of(
              "accountCode",
              conflict.accountCode().value(),
              "existingNormalBalance",
              conflict.existingNormalBalance().name(),
              "requestedNormalBalance",
              conflict.requestedNormalBalance().name());
    };
  }

  private static RejectedEnvelope queryRejectedEnvelope(BookQueryRejection rejection) {
    return new RejectedEnvelope(
        "rejected",
        BookQueryRejection.wireCode(rejection),
        queryRejectionMessage(rejection),
        null,
        queryRejectionDetails(rejection));
  }

  private static String queryRejectionMessage(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + OPEN_BOOK_OPERATION
              + ".";
      case BookQueryRejection.UnknownAccount unknownAccount ->
          "Account '%s' is not declared in this book."
              .formatted(unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          "Posting '%s' does not exist in this book."
              .formatted(postingNotFound.postingId().value());
    };
  }

  private static @Nullable Object queryRejectionDetails(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ -> null;
      case BookQueryRejection.UnknownAccount unknownAccount ->
          Map.of("accountCode", unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          Map.of("postingId", postingNotFound.postingId().value());
    };
  }

  private static DeclaredAccountPayload accountPayload(DeclaredAccount account) {
    return new DeclaredAccountPayload(
        account.accountCode().value(),
        account.accountName().value(),
        account.normalBalance().name(),
        account.active(),
        account.declaredAt().toString());
  }

  private static PostingPayload postingPayload(PostingFact postingFact) {
    return new PostingPayload(
        postingFact.postingId().value(),
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.provenance().requestProvenance().actorId().value(),
        postingFact.provenance().requestProvenance().actorType().name(),
        postingFact.provenance().requestProvenance().commandId().value(),
        postingFact.provenance().requestProvenance().idempotencyKey().value(),
        postingFact.provenance().requestProvenance().causationId().value(),
        postingFact
            .provenance()
            .requestProvenance()
            .correlationId()
            .map(value -> value.value())
            .orElse(null),
        postingFact
            .provenance()
            .requestProvenance()
            .reason()
            .map(value -> value.value())
            .orElse(null),
        postingFact.provenance().sourceChannel().name(),
        postingFact
            .reversalReference()
            .map(reference -> new ReversalPayload(reference.priorPostingId().value()))
            .orElse(null),
        postingFact.journalEntry().lines().stream().map(CliResponseWriter::linePayload).toList());
  }

  private static JournalLinePayload linePayload(dev.erst.fingrind.core.JournalLine line) {
    return new JournalLinePayload(
        line.accountCode().value(),
        line.side().name(),
        line.amount().currencyCode().value(),
        line.amount().amount().toPlainString());
  }

  private static PostingListPayload postingPagePayload(PostingPage page) {
    return new PostingListPayload(
        page.limit(),
        page.offset(),
        page.hasMore(),
        page.postings().stream().map(CliResponseWriter::postingPayload).toList());
  }

  private static AccountListPayload accountPagePayload(AccountPage page) {
    return new AccountListPayload(
        page.limit(),
        page.offset(),
        page.hasMore(),
        page.accounts().stream().map(CliResponseWriter::accountPayload).toList());
  }

  private static AccountBalancePayload accountBalancePayload(AccountBalanceSnapshot snapshot) {
    return new AccountBalancePayload(
        snapshot.account().accountCode().value(),
        snapshot.account().accountName().value(),
        snapshot.account().normalBalance().name(),
        snapshot.account().active(),
        snapshot.account().declaredAt().toString(),
        snapshot.effectiveDateFrom().map(Object::toString).orElse(null),
        snapshot.effectiveDateTo().map(Object::toString).orElse(null),
        snapshot.balances().stream().map(CliResponseWriter::balancePayload).toList());
  }

  private static BalanceBucketPayload balancePayload(CurrencyBalance balance) {
    return new BalanceBucketPayload(
        balance.debitTotal().currencyCode().value(),
        balance.debitTotal().amount().toPlainString(),
        balance.creditTotal().amount().toPlainString(),
        balance.netAmount().amount().toPlainString(),
        balance.balanceSide().name());
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
    return JsonMapper.builder()
        .changeDefaultPropertyInclusion(
            value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
        .build();
  }

  private static @Nullable String stringOrNull(@Nullable Object value) {
    return value == null ? null : value.toString();
  }

  private record SuccessEnvelope(String status, Object payload) {}

  private record FailureEnvelope(
      String status,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument) {}

  private record PreflightAcceptedEnvelope(
      String status, String idempotencyKey, String effectiveDate) {}

  private record CommittedEnvelope(
      String status,
      String postingId,
      String idempotencyKey,
      String effectiveDate,
      String recordedAt) {}

  private record RejectedEnvelope(
      String status,
      String code,
      String message,
      @Nullable String idempotencyKey,
      @Nullable Object details) {}

  private record OpenBookPayload(String bookFile, String initializedAt) {}

  private record GeneratedBookKeyFilePayload(
      String bookKeyFile, String encoding, int entropyBits, String permissions) {}

  private record RekeyBookPayload(String bookFile) {}

  private record DeclaredAccountPayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt) {}

  private record BookInspectionPayload(
      String bookFile,
      String state,
      boolean initialized,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      @Nullable Integer applicationId,
      @Nullable Integer detectedBookFormatVersion,
      int supportedBookFormatVersion,
      String migrationPolicy,
      @Nullable String initializedAt) {}

  private record PostingPayload(
      String postingId,
      String effectiveDate,
      String recordedAt,
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId,
      @Nullable String reason,
      String sourceChannel,
      @Nullable ReversalPayload reversal,
      List<JournalLinePayload> lines) {}

  private record ReversalPayload(String priorPostingId) {}

  private record JournalLinePayload(
      String accountCode, String side, String currencyCode, String amount) {}

  private record PostingListPayload(
      int limit, int offset, boolean hasMore, List<PostingPayload> postings) {}

  private record AccountListPayload(
      int limit, int offset, boolean hasMore, List<DeclaredAccountPayload> accounts) {}

  private record AccountBalancePayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      List<BalanceBucketPayload> balances) {}

  private record BalanceBucketPayload(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      String balanceSide) {}
}
