package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.Objects;

/** Canonical human-readable rejection prose for public rejection contracts. */
public final class RejectionNarrative {
  private static final String OPEN_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.OPEN_BOOK);

  private RejectionNarrative() {}

  /** Returns the canonical human-readable message for an administration rejection. */
  public static String message(BookAdministrationRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookAdministrationRejection.BookAlreadyInitialized _ ->
          "The selected book is already initialized.";
      case BookAdministrationRejection.BookNotInitialized _ ->
          "The selected book does not exist or has not been initialized with "
              + OPEN_BOOK_OPERATION
              + ".";
      case BookAdministrationRejection.BookContainsSchema _ ->
          "The selected SQLite file already contains schema objects and cannot be initialized as a new book.";
      case BookAdministrationRejection.AccountTypeConflict accountTypeConflict ->
          "Account '%s' already exists with account type '%s'; FinGrind will not amend it to '%s'."
              .formatted(
                  accountTypeConflict.accountCode().value(),
                  accountTypeConflict.existingAccountType().wireValue(),
                  accountTypeConflict.requestedAccountType().wireValue());
      case BookAdministrationRejection.AccountRoleConflict accountRoleConflict ->
          "Account '%s' already exists with account role '%s'; FinGrind will not amend it to '%s'."
              .formatted(
                  accountRoleConflict.accountCode().value(),
                  accountRoleConflict.existingAccountRole().wireValue(),
                  accountRoleConflict.requestedAccountRole().wireValue());
      case BookAdministrationRejection.RetainedEarningsAccountMissing rejectionMissing ->
          "Retained-earnings account '%s' is not declared in this book."
              .formatted(rejectionMissing.accountCode().value());
      case BookAdministrationRejection.RetainedEarningsAccountRoleMismatch rejectionRoleMismatch ->
          "Account '%s' has account role '%s' and cannot receive period-close earnings."
              .formatted(
                  rejectionRoleMismatch.accountCode().value(),
                  rejectionRoleMismatch.actualAccountRole().wireValue());
      case BookAdministrationRejection.RetainedEarningsAccountInactive rejectionInactive ->
          "Retained-earnings account '%s' is inactive and cannot receive closing entries."
              .formatted(rejectionInactive.accountCode().value());
      case BookAdministrationRejection.PeriodCloseMustStartAt rejectionStartAt ->
          "Close period must start at '%s' to preserve one contiguous close horizon."
              .formatted(rejectionStartAt.requiredEffectiveDateFrom());
      case BookAdministrationRejection.PeriodCloseFutureDate rejectionFutureDate ->
          "Close period cannot end after the current UTC date; requested '%s'."
              .formatted(rejectionFutureDate.attemptedEffectiveDateTo());
      case BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary rejectionBoundary ->
          "Close period '%s' through '%s' crosses this book's fiscal-year boundary '%s'."
              .formatted(
                  rejectionBoundary.attemptedEffectiveDateFrom(),
                  rejectionBoundary.attemptedEffectiveDateTo(),
                  rejectionBoundary.fiscalYearStart().wireValue());
    };
  }

  /** Returns the canonical human-readable message for a query rejection. */
  public static String message(BookQueryRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
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

  /** Returns the canonical human-readable message for a posting rejection. */
  public static String message(PostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
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
      case PostingRejection.PostingKindReserved postingKindReserved ->
          "Posting kind '%s' is reserved for generated FinGrind workflows and cannot be submitted directly."
              .formatted(postingKindReserved.postingKind().wireValue());
      case PostingRejection.BookFunctionalCurrencyMismatch functionalCurrencyMismatch ->
          "Posting currency '%s' does not match this book's functional currency '%s'."
              .formatted(
                  functionalCurrencyMismatch.attemptedCurrency().code(),
                  functionalCurrencyMismatch.functionalCurrency().code());
      case PostingRejection.ClosedPeriodViolation rejectionClosedPeriod ->
          "Posting effective date '%s' falls inside the closed-through horizon ending '%s'."
              .formatted(
                  rejectionClosedPeriod.attemptedEffectiveDate(),
                  rejectionClosedPeriod.closedThroughEffectiveDate());
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          "Opening-balance postings are allowed only before the first committed posting in this book; the first blocking posting is '%s' on '%s'."
              .formatted(
                  rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
                  rejectionWindowClosed.firstBlockingEffectiveDate());
      case PostingRejection.OpeningBalanceTouchesNominalAccount openingBalanceNominal ->
          "Opening-balance postings may seed only asset, liability, or equity accounts; '%s' is '%s'."
              .formatted(
                  openingBalanceNominal.accountCode().value(),
                  openingBalanceNominal.accountType().wireValue());
      case PostingRejection.RetainedEarningsAccountReserved rejectionReserved ->
          "Retained-earnings account '%s' is reserved for generated period-close postings."
              .formatted(rejectionReserved.accountCode().value());
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
}
