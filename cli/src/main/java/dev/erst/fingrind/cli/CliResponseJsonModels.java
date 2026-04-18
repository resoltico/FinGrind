package dev.erst.fingrind.cli;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Package-private JSON response model used by the CLI transport renderer. */
interface CliResponseJsonModels {

  record SuccessEnvelope(String status, Object payload) {}

  record FailureEnvelope(
      String status,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument) {}

  record PreflightAcceptedEnvelope(String status, String idempotencyKey, String effectiveDate) {}

  record CommittedEnvelope(
      String status,
      String postingId,
      String idempotencyKey,
      String effectiveDate,
      String recordedAt) {}

  record RejectedEnvelope(
      String status,
      String code,
      String message,
      @Nullable String idempotencyKey,
      @Nullable Object details) {}

  record OpenBookPayload(String bookFile, String initializedAt) {}

  record GeneratedBookKeyFilePayload(
      String bookKeyFile, String encoding, int entropyBits, String permissions) {}

  record RekeyBookPayload(String bookFile) {}

  record DeclaredAccountPayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt) {}

  record MissingBookInspectionPayload(
      String bookFile,
      String state,
      boolean initialized,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int supportedBookFormatVersion,
      String migrationPolicy) {}

  record ExistingBookInspectionPayload(
      String bookFile,
      String state,
      boolean initialized,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      String migrationPolicy) {}

  record InitializedBookInspectionPayload(
      String bookFile,
      String state,
      boolean initialized,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      String migrationPolicy,
      String initializedAt) {}

  record PostingPayload(
      String postingId,
      String effectiveDate,
      String recordedAt,
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId,
      String sourceChannel,
      @Nullable ReversalPayload reversal,
      List<JournalLinePayload> lines) {}

  record ReversalPayload(String priorPostingId, String reason) {}

  record JournalLinePayload(String accountCode, String side, String currencyCode, String amount) {}

  record PostingListPayload(
      int limit, int offset, boolean hasMore, List<PostingPayload> postings) {}

  record AccountListPayload(
      int limit, int offset, boolean hasMore, List<DeclaredAccountPayload> accounts) {}

  record AccountBalancePayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      List<BalanceBucketPayload> balances) {}

  record BalanceBucketPayload(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      String balanceSide) {}

  record AccountStateViolationsDetails(List<AccountStateViolationPayload> violations) {}

  record AccountStateViolationPayload(String code, String accountCode) {}

  record PriorPostingDetails(String priorPostingId) {}

  record NormalBalanceConflictDetails(
      String accountCode, String existingNormalBalance, String requestedNormalBalance) {}

  record UnknownAccountDetails(String accountCode) {}

  record PostingNotFoundDetails(String postingId) {}

  record PlanRejectionDetails(LedgerPlanPayload plan) {}

  record LedgerPlanPayload(String planId, String status, LedgerExecutionJournalPayload journal) {}

  record LedgerExecutionJournalPayload(
      String planId,
      String status,
      String startedAt,
      String finishedAt,
      List<LedgerJournalEntryPayload> steps) {}

  record LedgerJournalEntryPayload(
      String stepId,
      String kind,
      @Nullable String detailKind,
      String status,
      String startedAt,
      String finishedAt,
      List<LedgerFactPayload> facts,
      @Nullable LedgerStepFailurePayload failure) {}

  record LedgerStepFailurePayload(String code, String message, List<LedgerFactPayload> facts) {}

  /** JSON shape for one typed ledger fact observation. */
  sealed interface LedgerFactPayload
      permits TextLedgerFactPayload,
          FlagLedgerFactPayload,
          CountLedgerFactPayload,
          GroupLedgerFactPayload {}

  record TextLedgerFactPayload(String kind, String name, String value)
      implements LedgerFactPayload {
    TextLedgerFactPayload(String name, String value) {
      this("text", name, value);
    }
  }

  record FlagLedgerFactPayload(String kind, String name, boolean value)
      implements LedgerFactPayload {
    FlagLedgerFactPayload(String name, boolean value) {
      this("flag", name, value);
    }
  }

  record CountLedgerFactPayload(String kind, String name, int value) implements LedgerFactPayload {
    CountLedgerFactPayload(String name, int value) {
      this("count", name, value);
    }
  }

  record GroupLedgerFactPayload(String kind, String name, List<LedgerFactPayload> facts)
      implements LedgerFactPayload {
    GroupLedgerFactPayload(String name, List<LedgerFactPayload> facts) {
      this("group", name, facts);
    }
  }
}
