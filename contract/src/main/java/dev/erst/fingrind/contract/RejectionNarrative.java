package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonical human-readable rejection prose and compact journal facts. */
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

  /** Returns rejection-specific facts suitable for ledger-plan step failures. */
  public static List<LedgerFact> facts(BookAdministrationRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookAdministrationRejection.BookAlreadyInitialized _ -> List.of();
      case BookAdministrationRejection.BookNotInitialized _ -> List.of();
      case BookAdministrationRejection.BookContainsSchema _ -> List.of();
      case BookAdministrationRejection.NormalBalanceConflict conflict ->
          List.of(
              LedgerFact.text("accountCode", conflict.accountCode().value()),
              LedgerFact.text(
                  "existingNormalBalance", conflict.existingNormalBalance().wireValue()),
              LedgerFact.text(
                  "requestedNormalBalance", conflict.requestedNormalBalance().wireValue()));
    };
  }

  /** Returns rejection-specific facts suitable for ledger-plan step failures. */
  public static List<LedgerFact> facts(BookQueryRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookQueryRejection.BookNotInitialized _ -> List.of();
      case BookQueryRejection.UnknownAccount unknownAccount ->
          List.of(LedgerFact.text("accountCode", unknownAccount.accountCode().value()));
      case BookQueryRejection.PostingNotFound postingNotFound ->
          List.of(LedgerFact.text("postingId", postingNotFound.postingId().value()));
    };
  }

  /** Returns rejection-specific facts suitable for ledger-plan step failures. */
  public static List<LedgerFact> facts(PostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case PostingRejection.BookNotInitialized _ -> List.of();
      case PostingRejection.AccountStateViolations violations -> accountStateFacts(violations);
      case PostingRejection.DuplicateIdempotencyKey _ -> List.of();
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          priorPostingFacts(reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          priorPostingFacts(reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          priorPostingFacts(reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  private static List<LedgerFact> accountStateFacts(
      PostingRejection.AccountStateViolations violations) {
    List<LedgerFact> facts = new ArrayList<>();
    facts.add(LedgerFact.count("violationCount", violations.violations().size()));
    for (PostingRejection.AccountStateViolation violation : violations.violations()) {
      switch (violation) {
        case PostingRejection.UnknownAccount unknownAccount ->
            facts.add(
                LedgerFact.group(
                    "violation",
                    List.of(
                        LedgerFact.text("code", PostingRejection.wireCode(unknownAccount)),
                        LedgerFact.text("accountCode", unknownAccount.accountCode().value()))));
        case PostingRejection.InactiveAccount inactiveAccount ->
            facts.add(
                LedgerFact.group(
                    "violation",
                    List.of(
                        LedgerFact.text("code", PostingRejection.wireCode(inactiveAccount)),
                        LedgerFact.text("accountCode", inactiveAccount.accountCode().value()))));
      }
    }
    return List.copyOf(facts);
  }

  private static List<LedgerFact> priorPostingFacts(String priorPostingId) {
    return List.of(LedgerFact.text("priorPostingId", priorPostingId));
  }
}
