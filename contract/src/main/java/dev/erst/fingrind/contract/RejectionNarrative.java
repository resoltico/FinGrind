package dev.erst.fingrind.contract;

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
      case BookAdministrationRejection.NormalBalanceConflict normalBalanceConflict ->
          "Account '%s' already exists with normal balance '%s'; FinGrind will not amend it to '%s'."
              .formatted(
                  normalBalanceConflict.accountCode().value(),
                  normalBalanceConflict.existingNormalBalance().wireValue(),
                  normalBalanceConflict.requestedNormalBalance().wireValue());
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
