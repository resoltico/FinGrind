package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;

/** Maps deterministic rejection families into the CLI JSON envelope model. */
final class CliRejectionPayloadMapper {
  private static final String OPEN_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.OPEN_BOOK);
  private static final String INSPECT_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.INSPECT_BOOK);
  private static final String LIST_ACCOUNTS_OPERATION =
      ProtocolCatalog.operationName(OperationId.LIST_ACCOUNTS);
  private static final String GET_POSTING_OPERATION =
      ProtocolCatalog.operationName(OperationId.GET_POSTING);
  private static final String LIST_POSTINGS_OPERATION =
      ProtocolCatalog.operationName(OperationId.LIST_POSTINGS);
  private static final String CLOSE_PERIOD_OPERATION =
      ProtocolCatalog.operationName(OperationId.CLOSE_PERIOD);

  private CliRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.RejectedEnvelope postingRejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        postingRejectionHint(rejection),
        requestIdempotencyKey,
        postingRejectionDetails(rejection));
  }

  static CliEnvelopeJsonModels.RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        administrationRejectionHint(rejection),
        null,
        administrationRejectionDetails(rejection));
  }

  static CliEnvelopeJsonModels.RejectedEnvelope queryRejectedEnvelope(
      BookQueryRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        BookQueryRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        queryRejectionHint(rejection),
        null,
        queryRejectionDetails(rejection));
  }

  private static String postingRejectionHint(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "Run "
              + OPEN_BOOK_OPERATION
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case PostingRejection.AccountStateViolations _ ->
          "Declare or reactivate every account named in details.violations, then rerun the request with a fresh provenance.idempotencyKey.";
      case PostingRejection.DuplicateIdempotencyKey _ ->
          "Inspect the already-committed posting for this idempotency key instead of retrying the same key, or submit a new posting with a fresh provenance.idempotencyKey.";
      case PostingRejection.PostingKindReserved _ ->
          "Use postingKind STANDARD or OPENING_BALANCE on direct posting requests; let "
              + CLOSE_PERIOD_OPERATION
              + " generate PERIOD_CLOSE entries.";
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          "Use the selected book's functional currency for every journal line in this request, or open a separate book for another currency.";
      case PostingRejection.ClosedPeriodViolation _ ->
          "Use an effective date after the closed-through horizon, or close the next contiguous reporting period before posting into later dates.";
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          "Opening balances are only accepted before the first committed posting in the book. The window closed with "
              + rejectionWindowClosed.firstBlockingPostingKind().wireValue()
              + " on "
              + rejectionWindowClosed.firstBlockingEffectiveDate()
              + "; create a new book if the opening statement was not seeded completely.";
      case PostingRejection.OpeningBalanceTouchesNominalAccount _ ->
          "Opening-balance postings may seed only asset, liability, or equity accounts. Move revenue and expense setup into real operating-period postings instead.";
      case PostingRejection.RetainedEarningsAccountReserved _ ->
          "Post directly to ordinary accounts only; let "
              + CLOSE_PERIOD_OPERATION
              + " generate retained-earnings postings automatically.";
      case PostingRejection.ReversalTargetNotFound _ ->
          "Use "
              + GET_POSTING_OPERATION
              + " or "
              + LIST_POSTINGS_OPERATION
              + " to confirm the prior posting id before retrying the reversal.";
      case PostingRejection.ReversalAlreadyExists _ ->
          "Inspect the existing reversal for the referenced posting instead of retrying another reversal.";
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          "Build a full negating journal entry for the referenced posting so every line, amount, and side inverts the original exactly.";
    };
  }

  private static String administrationRejectionHint(BookAdministrationRejection rejection) {
    if (rejection instanceof BookAdministrationRejection.BookAlreadyInitialized) {
      return "Use "
          + INSPECT_BOOK_OPERATION
          + " or the normal read/write commands for this book instead of rerunning "
          + OPEN_BOOK_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.BookNotInitialized) {
      return "Run "
          + OPEN_BOOK_OPERATION
          + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
    }
    if (rejection instanceof BookAdministrationRejection.BookContainsSchema) {
      return "Select an empty target path, or remove the unintended SQLite file before rerunning "
          + OPEN_BOOK_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.AccountTypeConflict
        || rejection instanceof BookAdministrationRejection.AccountRoleConflict) {
      return "Keep the existing account identity as declared, or choose a different accountCode for a differently classified account.";
    }
    if (rejection instanceof BookAdministrationRejection.RetainedEarningsAccountMissing) {
      return "Declare the selected retained-earnings account first, using accountType EQUITY and accountRole RETAINED_EARNINGS, then rerun "
          + CLOSE_PERIOD_OPERATION
          + " with --retained-earnings-account set to that code.";
    }
    if (rejection instanceof BookAdministrationRejection.RetainedEarningsAccountRoleMismatch) {
      return "Choose an account whose declared accountRole is RETAINED_EARNINGS, or redeclare the selected account with the correct doctrine before rerunning "
          + CLOSE_PERIOD_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.RetainedEarningsAccountInactive) {
      return "Redeclare the retained-earnings account to reactivate it, or declare the correct retained-earnings account before rerunning "
          + CLOSE_PERIOD_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.PeriodCloseMustStartAt) {
      return "Rerun "
          + CLOSE_PERIOD_OPERATION
          + " with the required --effective-date-from value and the next contiguous unclosed end date.";
    }
    if (rejection instanceof BookAdministrationRejection.PeriodCloseFutureDate) {
      return "Choose an --effective-date-to on or before the current UTC date, then rerun "
          + CLOSE_PERIOD_OPERATION
          + ".";
    }
    return "Choose --effective-date-from and --effective-date-to that remain inside one fiscal year for this book, then rerun "
        + CLOSE_PERIOD_OPERATION
        + ".";
  }

  private static String queryRejectionHint(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ ->
          "Run "
              + OPEN_BOOK_OPERATION
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case BookQueryRejection.UnknownAccount _ ->
          "Use "
              + LIST_ACCOUNTS_OPERATION
              + " to confirm the account code, or declare the missing account before rerunning the query.";
      case BookQueryRejection.PostingNotFound _ ->
          "Use "
              + LIST_POSTINGS_OPERATION
              + " or "
              + GET_POSTING_OPERATION
              + " with a known posting id from this book before rerunning the query.";
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      postingRejectionDetails(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> null;
      case PostingRejection.AccountStateViolations violations ->
          new CliRejectionJsonModels.AccountStateViolationsDetails(
              violations.violations().stream()
                  .map(CliRejectionPayloadMapper::accountStateViolationPayload)
                  .toList());
      case PostingRejection.DuplicateIdempotencyKey _ -> null;
      case PostingRejection.PostingKindReserved rejectionPostingKind ->
          new CliRejectionJsonModels.PostingKindDetails(
              rejectionPostingKind.postingKind().wireValue());
      case PostingRejection.BookFunctionalCurrencyMismatch rejectionCurrencyMismatch ->
          new CliRejectionJsonModels.FunctionalCurrencyMismatchDetails(
              rejectionCurrencyMismatch.functionalCurrency().code(),
              rejectionCurrencyMismatch.attemptedCurrency().code());
      case PostingRejection.ClosedPeriodViolation violation ->
          new CliRejectionJsonModels.ClosedPeriodViolationDetails(
              violation.closedThroughEffectiveDate().toString(),
              violation.attemptedEffectiveDate().toString());
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          new CliRejectionJsonModels.OpeningBalanceWindowClosedDetails(
              rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
              rejectionWindowClosed.firstBlockingEffectiveDate().toString());
      case PostingRejection.OpeningBalanceTouchesNominalAccount rejectionOpeningBalance ->
          new CliRejectionJsonModels.OpeningBalanceNominalAccountDetails(
              rejectionOpeningBalance.accountCode().value(),
              rejectionOpeningBalance.accountType().wireValue());
      case PostingRejection.RetainedEarningsAccountReserved rejectionReserved ->
          new CliRejectionJsonModels.RetainedEarningsAccountDetails(
              rejectionReserved.accountCode().value());
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          new CliRejectionJsonModels.PriorPostingDetails(
              reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          new CliRejectionJsonModels.PriorPostingDetails(
              reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          new CliRejectionJsonModels.PriorPostingDetails(
              reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  private static CliRejectionJsonModels.AccountStateViolationPayload accountStateViolationPayload(
      PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount unknownAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(unknownAccount), unknownAccount.accountCode().value());
      case PostingRejection.InactiveAccount inactiveAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(inactiveAccount), inactiveAccount.accountCode().value());
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      administrationRejectionDetails(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ -> null;
      case BookAdministrationRejection.BookNotInitialized _ -> null;
      case BookAdministrationRejection.BookContainsSchema _ -> null;
      case BookAdministrationRejection.AccountTypeConflict conflict ->
          new CliRejectionJsonModels.AccountTypeConflictDetails(
              conflict.accountCode().value(),
              conflict.existingAccountType().wireValue(),
              conflict.requestedAccountType().wireValue());
      case BookAdministrationRejection.AccountRoleConflict conflict ->
          new CliRejectionJsonModels.AccountRoleConflictDetails(
              conflict.accountCode().value(),
              conflict.existingAccountRole().wireValue(),
              conflict.requestedAccountRole().wireValue());
      case BookAdministrationRejection.RetainedEarningsAccountMissing conflict ->
          new CliRejectionJsonModels.RetainedEarningsAccountDetails(conflict.accountCode().value());
      case BookAdministrationRejection.RetainedEarningsAccountRoleMismatch conflict ->
          new CliRejectionJsonModels.RetainedEarningsAccountRoleMismatchDetails(
              conflict.accountCode().value(), conflict.actualAccountRole().wireValue());
      case BookAdministrationRejection.RetainedEarningsAccountInactive conflict ->
          new CliRejectionJsonModels.RetainedEarningsAccountDetails(conflict.accountCode().value());
      case BookAdministrationRejection.PeriodCloseMustStartAt conflict ->
          new CliRejectionJsonModels.PeriodCloseStartDetails(
              conflict.requiredEffectiveDateFrom().toString());
      case BookAdministrationRejection.PeriodCloseFutureDate conflict ->
          new CliRejectionJsonModels.PeriodCloseFutureDateDetails(
              conflict.attemptedEffectiveDateTo().toString());
      case BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary conflict ->
          new CliRejectionJsonModels.PeriodCloseFiscalYearDetails(
              conflict.attemptedEffectiveDateFrom().toString(),
              conflict.attemptedEffectiveDateTo().toString(),
              conflict.fiscalYearStart().wireValue());
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      queryRejectionDetails(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ -> null;
      case BookQueryRejection.UnknownAccount unknownAccount ->
          new CliRejectionJsonModels.UnknownAccountDetails(unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          new CliRejectionJsonModels.PostingNotFoundDetails(postingNotFound.postingId().value());
    };
  }
}
